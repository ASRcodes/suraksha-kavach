"""Train only on the training partition; calibrate on validation; report untouched test."""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
import math
import platform
import shutil
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
import numpy as np
import sklearn
from sklearn.ensemble import ExtraTreesClassifier
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import confusion_matrix, precision_recall_fscore_support
from data_builders import build_urls, build_texts, fingerprint, split_summary
from features import FEATURE_NAMES, PublicSuffixList, parse_url, url_features, normalize_text, heuristic_score
from portable import url_score, text_score

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT.parent / "app/src/main/assets"

def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":"), allow_nan=False), encoding="utf-8", newline="\n")

def operating_threshold(labels, scores, target_fpr=.005, minimum=.60, already_positive=None):
    labels, scores = np.asarray(labels), np.asarray(scores)
    negative = labels == 0
    if not negative.any(): raise ValueError("Calibration requires benign examples")
    fixed = np.zeros(len(labels), dtype=bool) if already_positive is None else np.asarray(already_positive)
    budget = max(0, math.floor(target_fpr * int(negative.sum())) - int((negative & fixed).sum()))
    available = np.sort(scores[negative & ~fixed])
    if not len(available): return 1.0000000000000002
    index = max(0, len(available) - budget - 1)
    return float(max(minimum, np.nextafter(available[index], np.inf)))

def metrics(labels, predicted):
    labels, predicted = np.asarray(labels), np.asarray(predicted, dtype=int)
    tn, fp, fn, tp = confusion_matrix(labels, predicted, labels=[0, 1]).ravel()
    precision, recall, f1, _ = precision_recall_fscore_support(labels, predicted, average="binary", zero_division=0)
    # Wilson upper bound makes the small-language-sample limitation visible.
    n = int(tn + fp); p = fp / n if n else 0.0; z = 1.96
    upper = ((p + z*z/(2*n)) + z * math.sqrt(p*(1-p)/n + z*z/(4*n*n))) / (1+z*z/n) if n else None
    return {"precision": float(precision), "recall": float(recall), "f1": float(f1),
            "fpr": float(fp / (fp + tn)) if fp + tn else None,
            "fpr_95pct_upper_bound": upper, "tp": int(tp), "fp": int(fp), "tn": int(tn), "fn": int(fn),
            "rows": len(labels)}

def export_forest(model):
    trees = []
    for estimator in model.estimators_:
        tree = estimator.tree_
        values = tree.value[:, 0, :]
        probabilities = values[:, 1] / values.sum(axis=1)
        trees.append({"left": tree.children_left.tolist(), "right": tree.children_right.tolist(),
                      "feature": tree.feature.tolist(), "threshold": tree.threshold.tolist(),
                      "value": probabilities.tolist()})
    return {"schema_version": 1, "type": "extra_trees", "dtype": "float32",
            "feature_names": FEATURE_NAMES, "trees": trees}

def export_text(vectorizer, model):
    return {"schema_version": 1, "type": "char_tfidf_logistic", "normalization": "nfkc-lower-space-strip-url-v2",
            "ngram_min": 3, "ngram_max": 5, "max_codepoints": 4096, "sublinear_tf": True,
            "norm": "l2", "vocabulary": {k: int(v) for k, v in vectorizer.vocabulary_.items()},
            "idf": vectorizer.idf_.tolist(), "weights": model.coef_[0].tolist(), "bias": float(model.intercept_[0])}

def index_for(rows, split):
    return np.asarray([i for i, row in enumerate(rows) if row["split"] == split], dtype=np.int64)

def assert_isolated(rows):
    partitions = {split: {r["group"] for r in rows if r["split"] == split} for split in ("train", "validation", "test")}
    assert not partitions["train"] & partitions["test"]
    assert not partitions["train"] & partitions["validation"]
    assert not partitions["validation"] & partitions["test"]
    for split in partitions:
        if not {0, 1}.issubset({r["label"] for r in rows if r["split"] == split}):
            raise ValueError(f"{split} needs both classes")
    return {split: len(groups) for split, groups in partitions.items()}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", type=Path, default=ROOT / "data")
    parser.add_argument("--include-private-local-feeds", action="store_true",
                        help="Only use when feed rights permit training/export; never enabled for the public build.")
    args = parser.parse_args()
    started = time.perf_counter()
    psl_text = (args.data_dir / "public_suffix_list.dat").read_text(encoding="utf-8")
    psl = PublicSuffixList(psl_text)
    print("Preparing domain-separated URL records...", flush=True)
    urls, allowlist, url_cleaning = build_urls(args.data_dir, psl, args.include_private_local_feeds)
    texts, text_cleaning = build_texts(args.data_dir)
    print("URL rows:", len(urls), "message templates:", len(texts), flush=True)
    groups = {"url": assert_isolated(urls), "text": assert_isolated(texts)}
    u = {s: index_for(urls, s) for s in ("train", "validation", "test")}
    t = {s: index_for(texts, s) for s in ("train", "validation", "test")}
    x_url = np.empty((len(urls), len(FEATURE_NAMES)), dtype=np.float32)
    heuristic = np.zeros(len(urls))
    forced = np.zeros(len(urls), dtype=bool)
    for i, row in enumerate(urls):
        parsed = parse_url(row["url"])
        x_url[i] = url_features(parsed, psl)
        heuristic[i], forced[i], _ = heuristic_score(parsed, psl)
        if i and i % 50_000 == 0: print("Extracted URL features:", i, flush=True)
    y_url = np.asarray([r["label"] for r in urls], dtype=np.int8)
    y_text = np.asarray([r["label"] for r in texts], dtype=np.int8)
    forest = ExtraTreesClassifier(n_estimators=48, max_depth=14, min_samples_leaf=4,
        max_features=.9, class_weight="balanced", random_state=2100, n_jobs=1)
    print("Training URL forest using training data only...", flush=True)
    forest.fit(x_url[u["train"]], y_url[u["train"]])
    print("Training multilingual character model...", flush=True)
    vectorizer = TfidfVectorizer(analyzer="char", ngram_range=(3, 5), preprocessor=normalize_text,
        lowercase=False, min_df=2, max_features=40_000, sublinear_tf=True, norm="l2", dtype=np.float64)
    x_text_train = vectorizer.fit_transform([texts[i]["text"] for i in t["train"]])
    logistic = LogisticRegression(C=4.0, class_weight="balanced", solver="liblinear", max_iter=400, random_state=2100)
    logistic.fit(x_text_train, y_text[t["train"]])
    def predict_text(messages):
        scores = logistic.predict_proba(vectorizer.transform(messages))[:, 1]
        scores[[not normalize_text(message) for message in messages]] = 0.0
        return scores
    url_model = export_forest(forest)
    text_model = export_text(vectorizer, logistic)
    validation_text_scores = predict_text([texts[i]["text"] for i in t["validation"]])
    text_model["suspicious_threshold"] = operating_threshold(y_text[t["validation"]], validation_text_scores, .005, .60)
    text_model["dangerous_threshold"] = max(text_model["suspicious_threshold"],
        operating_threshold(y_text[t["validation"]], validation_text_scores, .0025, .80))
    # Jointly calibrate the URL alert pipeline on URL validation rows with the URL as message text.
    validation_url_scores = forest.predict_proba(x_url[u["validation"]])[:, 1]
    url_text_validation = predict_text([urls[i]["url"] for i in u["validation"]])
    fixed_warning = (heuristic[u["validation"]] >= .45) | forced[u["validation"]] | (url_text_validation >= text_model["suspicious_threshold"])
    fixed_danger = (heuristic[u["validation"]] >= .75) | (url_text_validation >= text_model["dangerous_threshold"])
    validation_baseline_fpr = float(np.mean(fixed_warning[y_url[u["validation"]] == 0]))
    if validation_baseline_fpr > .01:
        raise ValueError(f"Fixed heuristic/text pipeline FPR {validation_baseline_fpr:.2%} already exceeds target on validation data")
    url_model["suspicious_threshold"] = operating_threshold(y_url[u["validation"]], validation_url_scores, .01, .60, fixed_warning)
    url_model["dangerous_threshold"] = max(url_model["suspicious_threshold"],
        operating_threshold(y_url[u["validation"]], validation_url_scores, .005, .80, fixed_danger))
    print("Thresholds frozen. Evaluating the held-out test partitions...", flush=True)
    test_url_scores = forest.predict_proba(x_url[u["test"]])[:, 1]
    test_text_scores = predict_text([texts[i]["text"] for i in t["test"]])
    url_text_test = predict_text([urls[i]["url"] for i in u["test"]])
    pipeline_warning = ((test_url_scores >= url_model["suspicious_threshold"]) |
        (heuristic[u["test"]] >= .45) | forced[u["test"]] | (url_text_test >= text_model["suspicious_threshold"]))
    pipeline_danger = ((test_url_scores >= url_model["dangerous_threshold"]) |
        (heuristic[u["test"]] >= .75) | (url_text_test >= text_model["dangerous_threshold"]))
    # Bundle reputation only from training groups; test/validation domains are never in the blocklist.
    benign_hosts = {parse_url(r["url"]).host for r in urls if r["label"] == 0 and r["split"] == "train"}
    blocklist = sorted({parse_url(r["url"]).host for r in urls
        if r["split"] == "train" and r["label"] == 1 and parse_url(r["url"]).host not in benign_hosts
        and r["source"] == "uci-phiusiil" and psl.registrable(parse_url(r["url"]).host) is not None})
    ASSETS.mkdir(parents=True, exist_ok=True)
    write_json(ASSETS / "url_model.json", url_model)
    write_json(ASSETS / "text_model.json", text_model)
    (ASSETS / "public_suffix_list.dat").write_text(psl_text, encoding="utf-8", newline="\n")
    (ASSETS / "top_domains.txt").write_text("# Reviewed Tranco subset; registrable domains only.\n" + "\n".join(allowlist) + "\n", encoding="utf-8", newline="\n")
    (ASSETS / "blocklist.txt").write_text("# UCI PhiUSIIL CC-BY-4.0; training hosts only; never from held-out partitions.\n" + "\n".join(blocklist) + "\n", encoding="utf-8", newline="\n")
    # Fixed deterministic sampling from TEST ONLY. Each pair checks both exported scorers.
    rng = np.random.default_rng(2100)
    url_sample = rng.choice(u["test"], 50, replace=False)
    text_sample = rng.choice(t["test"], 50, replace=False)
    parity = []
    portable_latencies = []
    for ui, ti in zip(url_sample, text_sample):
        before = time.perf_counter()
        parsed = parse_url(urls[ui]["url"])
        feature_row = url_features(parsed, psl)
        us = url_score(url_model, feature_row)
        ts = text_score(text_model, texts[ti]["text"])
        portable_latencies.append((time.perf_counter() - before) * 1000)
        expected_u = float(forest.predict_proba(x_url[ui:ui+1])[0, 1])
        expected_t = float(predict_text([texts[ti]["text"]])[0])
        assert abs(us - expected_u) < 1e-10 and abs(ts - expected_t) < 1e-10
        parity.append({"url": urls[ui]["url"], "message": texts[ti]["text"], "features": feature_row.tolist(),
            "url_score": us, "text_score": ts, "url_id": urls[ui]["id"], "message_id": texts[ti]["id"], "split": "test"})
    write_json(ROOT / "parity/score_samples.json", {"schema_version": 1, "samples": parity})
    # Demo material is selected after metrics are frozen; metrics always cover the entire test set.
    dangerous_urls = [int(i) for i, score in zip(u["test"], test_url_scores)
                      if urls[i]["label"] == 1 and score >= url_model["dangerous_threshold"]]
    safe_urls = [int(i) for i, warn in zip(u["test"], pipeline_warning) if urls[i]["label"] == 0 and not warn]
    demos = []
    for language in ("en", "hi", "hi-Latn", "pa"):
        candidates = [(int(i), float(score)) for i, score in zip(t["test"], test_text_scores)
                      if texts[i]["language"] == language and texts[i]["label"] == 1]
        candidates.sort(key=lambda item: item[1], reverse=True)
        for index, (ti, score) in enumerate(candidates[:2]):
            if not dangerous_urls: break
            ui = dangerous_urls[(len(demos) + index) % len(dangerous_urls)]
            # Composed exclusively from two held-out records; never used to fit/calibrate a model.
            demos.append({"id": f"test-{language}-{index}", "language": language, "kind": "danger",
                "message": texts[ti]["text"] + "\n" + urls[ui]["url"], "expected_level": "DANGEROUS",
                "split": "test", "source_text_id": texts[ti]["id"], "source_url_id": urls[ui]["id"],
                "composed_from_held_out": True})
    for ui in safe_urls[:3]:
        demos.append({"id": "test-safe-" + urls[ui]["id"][:8], "language": "en", "kind": "safe",
            "message": urls[ui]["url"], "expected_level": "SAFE", "split": "test",
            "source_url_id": urls[ui]["id"], "composed_from_held_out": False})
    write_json(ASSETS / "demo_samples.json", {"schema_version": 1, "selection": "Correctly detected held-out examples, after threshold freeze", "samples": demos})
    report = {
        "created_utc": datetime.now(timezone.utc).isoformat(), "python": platform.python_version(),
        "sklearn": sklearn.__version__, "numpy": np.__version__, "grouping_version": "suraksha-split-v1",
        "evaluation_note": "Re-evaluated after fixing URL-syntax leakage into language features. Original group assignments retained; test rows never supplied to fit or threshold selection.",
        "split_policy": "SHA256 group assignment: 70% train / 15% validation / 15% test; exact groups disjoint.",
        "url_splits": split_summary(urls), "text_splits": split_summary(texts), "distinct_groups": groups,
        "sources_used": {"urls": dict(Counter(r["source"] for r in urls)), "messages": dict(Counter(r["source"] for r in texts))},
        "cleaning": {"urls": url_cleaning, "text": text_cleaning},
        "thresholds": {k: {"suspicious": m["suspicious_threshold"], "dangerous": m["dangerous_threshold"]}
                       for k, m in (("url", url_model), ("text", text_model))},
        "url_model_test": metrics(y_url[u["test"]], test_url_scores >= url_model["suspicious_threshold"]),
        "url_pipeline_test": metrics(y_url[u["test"]], pipeline_warning),
        "url_dangerous_test": metrics(y_url[u["test"]], pipeline_danger),
        "text_model_test": metrics(y_text[t["test"]], test_text_scores >= text_model["suspicious_threshold"]),
        "text_dangerous_test": metrics(y_text[t["test"]], test_text_scores >= text_model["dangerous_threshold"]),
        "per_language_test": {},
        "latency_python_portable_ms": {"median": float(np.median(portable_latencies)), "p95": float(np.percentile(portable_latencies,95)),
            "scope": "One URL parse/features/forest plus one message char model, 50 held-out pairs. Desktop, not phone."},
        "allowlist_domains": len(allowlist), "blocklist_hosts": len(blocklist), "parity_samples": 50,
        "training_wall_seconds": time.perf_counter() - started,
        "limitations": [
            "Multilingual data is synthetic and limited after deduplication; report per-language support and uncertainty.",
            "UCI English spam is an imperfect proxy for financial scams.",
            "Tranco popularity is a benign proxy, not proof of safety.",
            "No page content, redirects, DNS, WHOIS or network lookups are used.",
            "Model probabilities are risk scores, not calibrated guarantees.",
            "No physical-phone latency or real-world accuracy claim is made."
        ]
    }
    for language in ("en", "hi", "hi-Latn", "pa"):
        mask = np.asarray([texts[i]["language"] == language for i in t["test"]])
        report["per_language_test"][language] = metrics(y_text[t["test"]][mask], test_text_scores[mask] >= text_model["suspicious_threshold"])
    reports = ROOT / "reports"; reports.mkdir(exist_ok=True)
    write_json(reports / "metrics.json", report)
    source_manifest = json.loads((args.data_dir / "sources_manifest.json").read_text())
    write_json(reports / "sources_manifest.json", source_manifest)
    write_json(reports / "split_manifest.json", {
        "policy": report["split_policy"],
        "url": {split: fingerprint("\n".join(sorted(urls[i]["id"] for i in u[split]))) for split in u},
        "text": {split: fingerprint("\n".join(sorted(texts[i]["id"] for i in t[split]))) for split in t},
        "groups_disjoint": True,
        "test_demo_ids": [r.get("source_url_id") for r in demos] + [r.get("source_text_id") for r in demos if r.get("source_text_id")]
    })
    asset_metadata = {"generated_utc": report["created_utc"], "schema_version":1,
        "files": {name: hashlib.sha256((ASSETS/name).read_bytes()).hexdigest() for name in
            ("url_model.json","text_model.json","top_domains.txt","blocklist.txt","public_suffix_list.dat","demo_samples.json")}}
    write_json(ASSETS / "asset_manifest.json", asset_metadata)
    lines = ["# Held-out results", "", "Thresholds were selected on validation data only. Test rows were never used to fit models.",
             "An integration correction removed URL syntax from language features; the same original test partitions were re-evaluated. See integration_correction.json.",
             "", "| Evaluation | Rows | Precision | Recall | F1 | FPR |", "|---|---:|---:|---:|---:|---:|"]
    for key in ("url_model_test","url_pipeline_test","url_dangerous_test","text_model_test","text_dangerous_test"):
        m = report[key]
        lines.append(f"| {key} | {m['rows']} | {m['precision']:.4f} | {m['recall']:.4f} | {m['f1']:.4f} | {m['fpr']:.4%} |")
    lines += ["", "## Language-specific text results", "", "| Language | Rows | Precision | Recall | F1 | FPR | 95% FPR upper bound |",
        "|---|---:|---:|---:|---:|---:|---:|"]
    for lang,m in report["per_language_test"].items():
        fpr = "n/a" if m["fpr"] is None else f"{m['fpr']:.2%}"
        upper = "n/a" if m["fpr_95pct_upper_bound"] is None else f"{m['fpr_95pct_upper_bound']:.2%}"
        lines.append(f"| {lang} | {m['rows']} | {m['precision']:.4f} | {m['recall']:.4f} | {m['f1']:.4f} | {fpr} | {upper} |")
    lat = report["latency_python_portable_ms"]
    lines += ["", f"Desktop portable inference: median {lat['median']:.2f} ms; p95 {lat['p95']:.2f} ms.",
              "This includes both models and feature extraction. Phone measurements are pending.",
              "", "## Limitations", ""] + ["- " + x for x in report["limitations"]]
    lines += ["", "Exact counts, thresholds, source checksums and split fingerprints are in the adjacent JSON files.",
              "The demo selects correct held-out examples; the table above includes every held-out example, including mistakes."]
    (reports / "METRICS.md").write_text("\n".join(lines)+"\n",encoding="utf-8", newline="\n")
    print(json.dumps({k: report[k] for k in ("url_model_test","url_pipeline_test","text_model_test","per_language_test","latency_python_portable_ms")}, indent=2), flush=True)

if __name__ == "__main__":
    main()
