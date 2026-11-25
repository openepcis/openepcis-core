# Bundled JSON-LD contexts of ref.openepcis.org

The JSON-LD contexts an EPCIS event of the OpenEPCIS DPP extensions names, served from the jar
so that such an event is expanded without a network request. `JsonLDUtil` reads every
`bundled-jsonld-contexts/manifest.json` on the classpath; the first entry for a URL wins, and a
URL that no manifest names is still loaded from the network.

## What is bundled

The `jsonldContextUrl` of every extension in `bundled-extension-schemas/manifest.json` of
`openepcis-custom-extension-service`, plus every ref.openepcis.org context those name in turn
(`dpp-core-context` imports `assessment-context` and scopes `gs1-shortcuts-scoped-context` under
`gs1:masterDataAvailableFor`): 13 files. Shortcut, operational and bridge contexts are for
EN 18223 product documents, not EPCIS events, and stay out, as does `product/benetag`, which is
not part of this edition.

These contexts map the prefix `gs1` to `https://ref.gs1.org/voc/`, as the current GS1 EPCIS
context (2.0.1, also served under the unversioned URL) does. They cannot be combined with the
versioned 2.0.0 GS1 context URL, whose `gs1` is `https://gs1.org/voc/`: both are protected.

## Source of truth

The files are copied from the `openepcis-dpp-ready` project, `extensions/<area>/<module>/context/`,
and served there as `https://ref.openepcis.org/extensions/<area>/<module>/<file>`. Do not
hand-edit them. They match the extension schemas in `bundled-extension-schemas`; refresh both
together, and `JsonLDUtilBundledContextTest` checks that every manifest entry is served from the
jar and that a battery event with master data expands.
