# Held-out results

Thresholds were selected on validation data only. Test rows were never used to fit models.
An integration correction removed URL syntax from language features; the same original test partitions were re-evaluated. See integration_correction.json.

| Evaluation | Rows | Precision | Recall | F1 | FPR |
|---|---:|---:|---:|---:|---:|
| url_model_test | 41112 | 0.9988 | 0.9892 | 0.9940 | 0.0648% |
| url_pipeline_test | 41112 | 0.9984 | 0.9892 | 0.9938 | 0.0915% |
| url_dangerous_test | 41112 | 0.9997 | 0.9877 | 0.9937 | 0.0152% |
| text_model_test | 1284 | 0.9935 | 0.9505 | 0.9715 | 0.2081% |
| text_dangerous_test | 1284 | 1.0000 | 0.9226 | 0.9597 | 0.0000% |

## Language-specific text results

| Language | Rows | Precision | Recall | F1 | FPR | 95% FPR upper bound |
|---|---:|---:|---:|---:|---:|---:|
| en | 904 | 0.9841 | 0.8857 | 0.9323 | 0.26% | 0.95% |
| hi | 128 | 1.0000 | 1.0000 | 1.0000 | 0.00% | 6.64% |
| hi-Latn | 176 | 1.0000 | 1.0000 | 1.0000 | 0.00% | 3.66% |
| pa | 76 | 1.0000 | 1.0000 | 1.0000 | 0.00% | 8.38% |

Desktop portable inference: median 1.01 ms; p95 1.87 ms.
This includes both models and feature extraction. Phone measurements are pending.

## Limitations

- Multilingual data is synthetic and limited after deduplication; report per-language support and uncertainty.
- UCI English spam is an imperfect proxy for financial scams.
- Tranco popularity is a benign proxy, not proof of safety.
- No page content, redirects, DNS, WHOIS or network lookups are used.
- Model probabilities are risk scores, not calibrated guarantees.
- No physical-phone latency or real-world accuracy claim is made.

Exact counts, thresholds, source checksums and split fingerprints are in the adjacent JSON files.
The demo selects correct held-out examples; the table above includes every held-out example, including mistakes.
