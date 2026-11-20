# Offline training and reproducibility

Run with Python 3.11 and the pinned packages in requirements.txt. Raw data remains in
`ml/data` (gitignored); exported models and reports are public.

```powershell
py -3.11 -m venv .venv
.venv\Scripts\python -m pip install -r ml/requirements.txt
.venv\Scripts\python ml/fetch_data.py
.venv\Scripts\python ml/train.py
```

Only static archives/feeds are downloaded. Links inside the datasets are never opened.
The two exported JSON models are scored directly by Kotlin; no ML runtime ships in the app.

## Declared datasets

- UCI PhiUSIIL by Prasad and Chandra (2024), CC BY 4.0: URL strings and inverted original labels
  (original 1 = legitimate; our 1 = malicious). Only URL-derived features are used.
- Tranco: popularity data used as a benign-domain proxy. Provider terms include CC BY 3.0,
  CC BY-SA 4.0 and CC BY-NC 4.0; this is a noncommercial hackathon/research build. Popularity
  does not establish that a website is safe. The published allowlist is a small reviewed subset.
- UCI SMS Spam Collection by Almeida and Hidalgo, CC BY 4.0. Spam is an imperfect proxy for scam;
  the report distinguishes this source from scam-specific synthetic messages.
- anmolshrivastav/scam_ham_india_14_languages, MIT, pinned revision
  `34e2c413aefd2e8f2a6e93177a155f29733290a5`: synthetic English, Hindi, Hinglish and Punjabi.
- sidzzz07/scamshield-dataset, publisher-declared MIT, pinned revision
  `059dee884476a45e380808e68d09e74baff67291`: only publisher-generated `Synthetic_Tier_C`
  rows from the upstream training file; third-party merged rows are excluded.
- Public Suffix List, Mozilla/contributors, MPL 2.0: offline ICANN and private suffix rules.
- Optional local OpenPhish URL-text and URLhaus CSV importers are supported. Neither restricted
  feed is redistributed. OpenPhish redistribution requires permission; URLhaus downloads
  currently require an Auth-Key. No API key is embedded.

The downloader records source URLs, fetch time and SHA-256. The training report records the
actual input sources, group splits, operating thresholds and measured held-out metrics.
Synthetic-language results are not evidence of field accuracy.
