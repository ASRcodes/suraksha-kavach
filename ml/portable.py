"""Reference scorers for the exported JSON. Kotlin must produce the same numbers."""
from collections import Counter
import math
from features import normalize_text

def url_score(model, features):
    total = 0.0
    for tree in model["trees"]:
        node = 0
        while tree["feature"][node] >= 0:
            index = tree["feature"][node]
            node = tree["left"][node] if features[index] <= tree["threshold"][node] else tree["right"][node]
        total += tree["value"][node]
    return total / len(model["trees"])

def text_score(model, text):
    text = normalize_text(text)
    if not text: return 0.0
    counts = Counter()
    vocabulary = model["vocabulary"]
    for size in range(model["ngram_min"], model["ngram_max"] + 1):
        for i in range(len(text) - size + 1):
            index = vocabulary.get(text[i:i + size])
            if index is not None: counts[index] += 1
    norm = weighted = 0.0
    for index, count in counts.items():
        value = (1.0 + math.log(count)) * model["idf"][index]
        norm += value * value
        weighted += value * model["weights"][index]
    logit = model["bias"] + (weighted / math.sqrt(norm) if norm else 0.0)
    return 1 / (1 + math.exp(-logit)) if logit >= 0 else math.exp(logit) / (1 + math.exp(logit))
