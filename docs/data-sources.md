# Public data sources

## FDA/openFDA drug shortages

Canonical endpoint: `https://api.fda.gov/drug/shortages.json`

Official references:

- [dataset overview](https://open.fda.gov/data/drugshortages/)
- [endpoint guide](https://open.fda.gov/apis/drug/drugshortages/how-to-use-the-endpoint/)
- [searchable fields](https://open.fda.gov/apis/drug/drugshortages/searchable-fields/)
- [bulk download notes](https://open.fda.gov/apis/drug/drugshortages/download/)

RxRelay inspected and paged the live endpoint on 2026-08-22. At that observation, API metadata reported 1,628 records and `last_updated=2026-08-22`. This is a dated development observation, not a promised count. The actual response contained the following top-level record fields:

| FDA field | Meaning/use in RxRelay | Availability observed in that snapshot |
|---|---|---:|
| `package_ndc` | source product identifier component | 1,628 / 1,628 |
| `generic_name` | original medication name | 1,628 / 1,628 |
| `company_name` | source company name | 1,628 / 1,628 |
| `presentation` | package/presentation text | 1,628 / 1,628 |
| `status` | source status | 1,628 / 1,628 |
| `update_type` | New, Revised, or Reverified | 1,628 / 1,628 |
| `update_date` | FDA record update date | 1,628 / 1,628 |
| `initial_posting_date` | initial FDA posting date | 1,628 / 1,628 |
| `dosage_form` | source dosage form | 1,611 / 1,628 |
| `availability` | availability narrative/value | 1,177 / 1,628 |
| `related_info` | related source text | 1,122 / 1,628 |
| `shortage_reason` | reported reason | 421 / 1,628 |
| `discontinued_date` | reported discontinuation date | 440 / 1,628 |
| `related_info_link` | related URL | 45 / 1,628 |
| `change_date` | reported change date | 16 / 1,628 |
| `resolved_note` | reported resolution note | 9 / 1,628 |
| `openfda` | optional FDA annotations, including possible RxCUI/NDC arrays | 1,429 / 1,628 |

`contact_info` and `therapeutic_category` also occur. Contact information is validated but deliberately not persisted. The nested `openfda` annotation can include arrays such as `brand_name`, `generic_name`, `manufacturer_name`, `product_type`, `route`, `substance_name`, `rxcui`, and NDC/SPL identifiers. RxRelay currently consumes only an unambiguous `rxcui`; it does not promote annotation arrays into unsupported shortage fields. Counts can change whenever FDA republishes or corrects records.

### Source limitations

- The endpoint provides no stable primary record ID. `package_ndc` is not unique: the inspected snapshot had 1,581 distinct values for 1,628 records.
- A record's absence is not documented as meaning resolved. RxRelay only records resolution when FDA explicitly supplies a resolvable status.
- Old records may be corrected in current downloads; RxRelay cannot reconstruct revisions made before its first observation.
- Availability is not inventory quantity. The feed does not reliably provide geographic, pharmacy/facility, duration, demand, substitution, clinical recommendation, or patient-specific availability data.
- Optional fields are stored as unavailable when absent; they are never invented from adjacent text.

## NIH RxNorm

Canonical API root: `https://rxnav.nlm.nih.gov/REST`

RxRelay first uses a source-provided `openfda.rxcui` only when it contains exactly one distinct value. Otherwise it calls [`findRxcuiByString`](https://lhncbc.nlm.nih.gov/RxNav/APIs/api-RxNorm.findRxcuiByString.html) with `search=2` (exact or normalized match) and `allsrc=0`, then accepts only exactly one active RxCUI. It retrieves concept properties for the canonical name. Zero results are `UNRESOLVED`; multiple results are `AMBIGUOUS`; transport/server failures are `ERROR`. No fuzzy candidate ranking or forced match is performed.

Results, including negative and ambiguous results, are cached in a bounded in-process TTL cache. The cache limits repeat traffic but is not authoritative and is safe to lose.

## Source-provided versus derived

Source text/dates, FDA status, and package NDC are preserved as source values. A single unambiguous FDA-annotated RxCUI may seed normalization; other nested annotations remain represented by the full source payload hash rather than copied into columns. RxCUI/canonical name plus normalization outcome are normalized identity fields. `source_record_id`, normalized status enum, payload hash, state fingerprint, observation timestamp, run ID, and event IDs are RxRelay-derived metadata. The API and UI expose these categories separately.

The openFDA NDC endpoint is not queried by the current pipeline. It remains a possible future enrichment source and is not claimed as implemented.

## Canonical release ingestion observation

The release workflow ran a bounded live request rather than seeding captured fixtures:

```powershell
Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:8090/api/v1/ingestions?limit=100'
```

The first run returned and persisted the following actual summary on 2026-08-24:

| Field | Observed value |
|---|---|
| Run ID | `c0b630d8-baf5-449d-baca-4251f6d1f889` |
| Source | `openfda` |
| Started | `2026-08-24T18:00:56.081137Z` |
| Completed | `2026-08-24T18:00:59.636460Z` |
| Requested / fetched / published | 100 / 100 / 100 |
| Malformed/rejected | 0 |
| Run-reported unresolved / ambiguous | 6 / 75 |
| Ingestion errors | 0 |

After Kafka consumer lag reached zero, PostgreSQL contained 58 medication concepts, 45 manufacturers, 100 products, 100 current shortage rows, 100 observations, and 100 meaningful status-change rows. The shortage status distribution was 79 `CURRENT` and 21 `TO_BE_DISCONTINUED`. Joining all persisted shortage rows to their current medication identities yielded 10 `SOURCE_PROVIDED`, 5 `RESOLVED`, 78 `AMBIGUOUS`, and 7 `UNRESOLVED` normalization states.

The run summary’s six unresolved normalization attempts and the persisted join’s seven unresolved current medication associations answer different questions: ingestion counts source-record outcomes during that run, while the join reflects shared/deduplicated medication identities after all records are applied. They are reported separately rather than forced into one misleading “match rate.” A confident persisted association is either `SOURCE_PROVIDED` or `RESOLVED`; ambiguous candidates are never counted as matches.

A second live run (`fa1809ec-105b-445c-b48a-aa70d0395ac3`, `2026-08-24T18:01:45.813788Z` to `18:01:46.967028Z`) fetched and published the same 100 records with zero malformed records. Observations increased from 100 to 200 and processed lifecycle/observation events increased from 102 to 204, while status changes and outbox rows remained exactly 100. That is the measured unchanged-snapshot behavior: provenance is retained without emitting duplicate availability events.

Subsequent counts elsewhere in the documentation include one clearly labeled synthetic Reliability Lab medication/shortage and its development transitions. The canonical live-ingestion-only counts above do not.
