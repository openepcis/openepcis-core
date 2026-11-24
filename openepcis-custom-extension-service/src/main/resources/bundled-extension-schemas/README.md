# Bundled DPP extension schemas

These permissive per-namespace JSON Schemas ship inside the repository image so a
fresh environment can capture EPCIS events that declare a `GS1-Extensions`
namespace (oec / eubat / eutex / …) without an operator first POSTing each schema
to `/userExtension/jsonSchema`.

`UserExtensionSchemaCache` loads `manifest.json` at startup and puts each schema
into the in-memory namespace→schema cache **only for namespaces not already
present in the `user-extension-schema` index** — so an operator-registered
(possibly stricter) schema always wins over the bundled default.

## Source of truth

These files are generated artifacts copied from the `openepcis-dpp-ready`
project. Do not hand-edit them. To refresh after the ontologies change:

```bash
# in openepcis-dpp-ready
pnpm run build:extension-schemas        # regenerates extensions/**/validation/*.extension-schema.json
                                        # + scripts/out/extension-schemas.manifest.json
```

then re-copy the generated `*.extension-schema.json` files here and rebuild
`manifest.json` (namespace → classpath resource). Leave out `benetag` and drop
every `x-access-level*` key: the access tiers are not part of this edition, and
`BundledExtensionSchemaManifestTest` fails on a schema that still carries one.
Further manifests can be put in front of this one with
`openepcis.user-extension.bundled-manifests`; the first that names a namespace wins. The generator's manifest and
this `manifest.json` carry the same namespace / defaultPrefix / jsonldContextUrl
fields; only the file reference differs (`schemaFile` path vs `resource`
classpath name).

After the namespace move to `ref.openepcis.org` (dpp-ready 0.10.0) the manifest
lists every module under its `.org` namespace first, followed by `ref.openepcis.io`
aliases for the nine modules bundled before the move. The schemas are permissive
and namespace-agnostic, so a stored or legacy event that still declares the `.io`
namespace keeps validating against the same file.
