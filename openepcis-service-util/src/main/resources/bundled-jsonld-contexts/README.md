# Bundled JSON-LD contexts of ref.openepcis.org

The JSON-LD contexts of the OpenEPCIS DPP extensions, served from the jar so that a document
naming them is expanded without a network request. `JsonLDUtil` reads every
`bundled-jsonld-contexts/manifest.json` on the classpath; the first entry for a URL wins, and a
URL that no manifest names is still loaded from the network.

## Source of truth

The files are copied from the `openepcis-dpp-ready` project, `extensions/<area>/<module>/context/`,
and served there as `https://ref.openepcis.org/extensions/<area>/<module>/<file>`. Do not
hand-edit them. They match the extension schemas in `bundled-extension-schemas` of
`openepcis-custom-extension-service`; refresh both together. Leave out `product/benetag`, which
is not part of this edition.
