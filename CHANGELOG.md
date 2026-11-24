# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), the versions follow the OpenEPCIS
platform release.

## [Unreleased]

## [0.9.4]

First public release under Apache-2.0, as the core of the OpenEPCIS Community Edition.

### Included
- Capture (Kafka), query with named queries, streaming and scheduled subscriptions, OpenSearch
  persistence, local and S3 document storage, event validation, user extension schemas.
- Single-tenant defaults: `SingleClientOpenSearchClient` and `DefaultUserDetailsExtractor`.
- Interfaces with open defaults for what the Business edition adds: `CaptureAuthorization`,
  `StoredEventListener`, `RuleSettings.Resolver`, `CallerTags`, `QueryScope`,
  `WebhookDestinationPolicy`.
- Offline validation: the EPCIS JSON schema and JSON-LD context are bundled.

### Fixed
- An unnamed event query answers with `queryName` `SimpleEventQuery`.
- A document whose `epcClass` is not a GS1 class identifier is rejected in validation instead
  of failing at persistence.

### Not included
- Authentication, multi-tenancy and document-level security, possession, commissioning
  register and rules, per-tenant settings and meters, the webhook destination guard, access
  tiers of the extension schemas, Azure Blob storage, the GS1 Germany service platform client,
  the legacy Elasticsearch backend. They live in the Business edition.
