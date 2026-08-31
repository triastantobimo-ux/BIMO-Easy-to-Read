# CP2 benchmark protocol

## Objective

Lock the production OCR engine using measured evidence on the Xiaomi 15T Pro. Vendor claims and
cloud compilation are not engine-selection evidence.

## Corpus

The owner supplies representative, non-confidential or approved samples with ground truth:

- Indonesian and English documents;
- single and multi-column pages;
- packaging/posters;
- wired Excel/worksheet tables;
- borderless tables;
- skew, perspective, glare, blur, and small text;
- decimals, negative values, Rupiah/IDR, percentages, dates, identifiers, and blank cells.

No simulated accuracy result may be mixed into the production score.

## Required ground truth

Each sample receives:

1. exact UTF-8 text;
2. reading order;
3. table row/column topology where applicable;
4. exact visible cell values;
5. visible value type/format class;
6. rotation and quality labels.

## Metrics

- CER and WER for text.
- Reading-order exact and block-order error.
- Cell exact match.
- Strict table exact match: topology and every visible cell must match.
- Numeric/value-type/format accuracy.
- Latency P50/P90, cold load, peak memory, APK size, and thermal observation.
- Fallback rate and REVIEW_REQUIRED rate.

## Acceptance

The engine is not described as production-locked until the corpus is reconciled. For Excel Ultra,
an automatic export passes only when topology is correct and no low-confidence visible cell remains.
A “100% verified” workbook requires explicit review of all flagged cells.

## Evidence output

The device run must export a machine-readable result file, sample-level exception log, aggregate
metrics, engine/model hashes, app commit, device/OS details, and reviewer sign-off.
