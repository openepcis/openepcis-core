/*
 * Copyright 2022-2026 benelog GmbH & Co. KG
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package io.openepcis.repository.opensearch.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.repository.opensearch.OpenSearchConfig;
import io.openepcis.repository.util.RollingIndexUtil;
import jakarta.enterprise.context.ApplicationScoped;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.*;
import org.opensearch.client.opensearch.indices.*;
import org.opensearch.client.opensearch.ingest.*;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

@ApplicationScoped
public class OpenSearchInitializer {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OpenSearchInitializer.class);
    public static final String STORE = "store";
    public static final String MAPPINGS = "mappings";
    public static final String PROPERTIES = "properties";
    public static final String TYPE = "type";
    public static final String ENABLED = "enabled";
    public static final String INDEX_PATTERNS = "index_patterns";
    public static final String SETTINGS = "settings";
    public static final String NUMBER_OF_SHARDS = "number_of_shards";
    public static final String NUMBER_OF_REPLICAS = "number_of_replicas";
    public static final String DYNAMIC_TEMPLATES = "dynamic_templates";

    /**
     * Priority of the templates this service installs.
     *
     * <p>It only has to beat the catch-all a cluster tends to grow — the one here
     * sits at 1 and exists to set a replica count — and it matches the rung the
     * other application templates on this cluster already occupy. A composable
     * template does not merge with a legacy one; the highest-priority composable
     * match wins outright, so being above the floor is the whole job.
     */
    public static final int TEMPLATE_PRIORITY = 200;
    public static final String MATCH_MAPPING_TYPE = "match_mapping_type";
    public static final String STRINGS_AS_KEYWORDS = "strings_as_keywords";
    public static final String ALIASES = "aliases";
    public static final String DEFAULT_PIPELINE = "default_pipeline";
    public static final String SET = "set";
    public static final String FIELD = "field";
    public static final String VALUE = "value";
    private final OpenSearchClient superUserClient;
    private final ObjectMapper mapper;
    private final OpenSearchConfig openSearchConfig;
    private final String[] indicesNames = new String[] {"user-extension-fields", "user-extension-schema"};
    private final Map<String, String> templateNames = Map.of("capture-job-template", "capture-job", "epcis-event-template", "epcis-event", "epc-template", "epc", "epcis-query-template", "epcis-query", "epcis-subscription-template", "epcis-subscription", "epcis-percolator-queries-template", "epcis-percolator-queries");

    // Define your PropertyTypeEnum if not already defined
    private enum PropertyTypeEnum {
        KEYWORD("keyword"), BOOLEAN("boolean"), OBJECT("object"), DATE("date"), LONG("long"), DOUBLE("double"), FLOAT("float"), PERCOLATOR("percolator"), NESTED("nested"), TEXT("text");
        private final String typeString;

        PropertyTypeEnum(String typeString) {
            this.typeString = typeString;
        }

        public static PropertyTypeEnum fromTypeString(String typeString) {
            for (PropertyTypeEnum type : PropertyTypeEnum.values()) {
                if (type.typeString.equalsIgnoreCase(typeString)) {
                    return type;
                }
            }
            return null;
        }
    }

    // create add_metadata_pipeline if missing in opensearch
    public void createIngestPipeline() {
        final String pipelineName = "add_metadata_pipeline";
        try {
            if (!ingestPipelineExists(pipelineName)) {
                final InputStream inputStream = getClass().getResourceAsStream(openSearchConfig.pipelines().addMetaDataPipeline());
                if (inputStream == null) {
                    throw new IOException("Pipeline JSON file not found");
                }
                final JsonNode pipelineNode = mapper.readTree(inputStream);
                final PutPipelineRequest request = PutPipelineRequest.of(builder -> {
                    return builder.id(pipelineName).description(pipelineNode.get("description").asText()).processors(parseProcessors(pipelineNode.get("processors")));
                });
                final PutPipelineResponse response = superUserClient.ingest().putPipeline(request);
                if (Boolean.TRUE.equals(response.acknowledged())) {
                    log.info("Ingest pipeline \'{}\' created successfully.", pipelineName);
                } else {
                    log.error("Failed to create ingest pipeline \'{}\'. Response: {}", pipelineName, response);
                }
            } else {
                log.info("Ingest pipeline \'{}\' already exists, skipping pipeline creation.", pipelineName);
            }
        } catch (IOException e) {
            log.error("Error while creating ingest pipeline \'{}\'", pipelineName, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes every index template at each start. The templates belong to the application: written
     * only when missing, a fix to one (objectValue unmapped, core !122) never reached a cluster that
     * had the old one. A put replaces the template and leaves existing indices as they are.
     */
    public void createTemplates() {
        for (Map.Entry<String, String> entry : templateNames.entrySet()) {
            createTemplate(entry.getKey(), getConfigProperty(entry.getKey()), entry.getValue());
        }
    }

    public void createIndices() {
        for (String indexName : indicesNames) {
            if (missingIndex(indexName)) {
                createIndex(indexName, createIndexProperties(getConfigProperty(indexName)));
            } else {
                log.info("Index \'{}\' already exists, skipping index creation.", indexName);
            }
        }
        openSearchConfig.indices().extra().forEach((indexName, mapping) -> {
            if (missingIndex(indexName)) {
                createIndex(indexName, createIndexProperties(mapping));
            } else {
                log.info("Index \'{}\' already exists, skipping index creation.", indexName);
            }
        });
    }

    private static List<Processor> parseProcessors(JsonNode processorsNode) {
        final List<Processor> processors = new ArrayList<>();
        if (processorsNode.isArray()) {
            for (JsonNode node : processorsNode) {
                if (node.has(SET)) {
                    JsonNode setNode = node.get(SET);
                    final SetProcessor setProcessor = new SetProcessor.Builder().field(setNode.get(FIELD).asText()).value(JsonData.of(setNode.get(VALUE))).build();
                    processors.add(Processor.of(builder -> builder.set(setProcessor)));
                }
            }
        }
        return processors;
    }

    private boolean ingestPipelineExists(String pipelineName) throws IOException {
        final GetPipelineResponse pipeline = superUserClient.ingest().getPipeline(GetPipelineRequest.of(builder -> builder.id(pipelineName)));
        return pipeline.result().containsKey(pipelineName);
    }

    private void createIndex(String indexName, Map<String, Property> propertyMap) {
        try {
            final CreateIndexRequest indexRequest = CreateIndexRequest.of(builder -> builder.index(indexName).mappings(mappingBuilder -> mappingBuilder.properties(propertyMap).source(sourceBuilder -> sourceBuilder.enabled(true))));
            final CreateIndexResponse createIndexResponse = superUserClient.indices().create(indexRequest);
            if (Boolean.TRUE.equals(createIndexResponse.acknowledged())) {
                log.info("index {} is successfully created", indexName);
            } else {
                log.error("An error occurred while creating index \'{}\'. Error: {}", indexName, createIndexResponse);
            }
        } catch (IOException e) {
            log.error("Error while creating index: {}", indexName, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Drop the legacy template of the same name, once the composable one is in place.
     *
     * <p>Left behind it is worse than useless: it reads as though it were in force,
     * and it is the first thing somebody consults when indices come out with the
     * wrong mapping. Failing to remove it is not worth a failed startup — the
     * composable template already governs — so this only complains.
     */
    private void removeSupersededLegacyTemplate(final String templateName) {
        try {
            if (Boolean.TRUE.equals(superUserClient.indices().existsTemplate(b -> b.name(templateName)).value())) {
                superUserClient.indices().deleteTemplate(b -> b.name(templateName));
                log.info("removed the superseded legacy template {}", templateName);
            }
        } catch (Exception e) {
            log.warn("could not remove the superseded legacy template {} — it is inert, but misleading", templateName, e);
        }
    }

    private void createTemplate(String templateName, String templatePath, String indexName) {
        try {
            // Read the template JSON file
            InputStream inputStream = getClass().getResourceAsStream(templatePath);
            if (inputStream == null) {
                throw new IOException("Template file not found");
            }
            JsonNode templateNode = mapper.readTree(inputStream);
            // Extracting index_patterns
            JsonNode indexPatternsNode = templateNode.get(INDEX_PATTERNS);
            String[] indexPatterns = mapper.treeToValue(indexPatternsNode, String[].class);
            // Extracting settings
            JsonNode settingsNode = templateNode.get(SETTINGS);
            String settings = mapper.writeValueAsString(settingsNode);
            // Extracting mappings
            JsonNode mappingsNode = templateNode.get(MAPPINGS);
            String mappings = mapper.writeValueAsString(mappingsNode);
            // Extracting aliases
            JsonNode aliasesNode = templateNode.get(ALIASES);
            String aliasesKey = aliasesNode.fieldNames().next();
            //Prepare settings
            final IndexSettings indexSettings = IndexSettings.of(builder -> {
                builder.numberOfShards(settingsNode.get(NUMBER_OF_SHARDS).asInt())
                        .numberOfReplicas(openSearchConfig.templates().numberOfReplicas().orElseGet(() -> settingsNode.get(NUMBER_OF_REPLICAS).asInt()));
                // Set defaultPipeline only if it exists in settingsNode
                final JsonNode defaultPipelineNode = settingsNode.get(DEFAULT_PIPELINE);
                if (Objects.nonNull(defaultPipelineNode)) {
                    builder.defaultPipeline(defaultPipelineNode.asText());
                }
                return builder;
            });
            // Extracting dynamic templates
            String matchMappingType = getDynamicTemplateValue(templateNode);
            final DynamicTemplate dynamicTemplate = DynamicTemplate.of(builder -> builder.matchMappingType(matchMappingType).mapping(Property.of(propBuilder -> propBuilder.keyword(KeywordProperty.of(keywordBuilder -> keywordBuilder)))));
            // Extract Properties
            final JsonNode propertiesNode = mappingsNode.path(PROPERTIES);
            final Map<String, Property> propertiesMap = new HashMap<>();
            parseProperties(propertiesNode, propertiesMap);
            // A composable template, not a legacy one. This is not a modernisation:
            // a composable template that matches an index pattern suppresses EVERY
            // legacy template for it, and clusters acquire catch-all composable
            // templates for innocent reasons. This one did — a `*` template with
            // priority 1 that sets nothing but number_of_replicas. From the day it
            // appeared, none of the mappings below reached a new index: metadata was
            // mapped dynamically as an object rather than nested, and since the
            // document-level security rule is a nested query on metadata, every
            // search against such an index failed at the shard level. Four months of
            // captures went into indices nothing could read.
            final PutIndexTemplateRequest templateRequest = PutIndexTemplateRequest.of(requestBuilder -> requestBuilder
                    .name(templateName)
                    .indexPatterns(List.of(indexPatterns))
                    // Above any catch-all, and the same rung the other application
                    // templates on this cluster already stand on.
                    .priority(TEMPLATE_PRIORITY)
                    .template(templateMapping -> templateMapping
                            .mappings(mappingBuilder -> mappingBuilder.dynamicTemplates(Map.of(STRINGS_AS_KEYWORDS, dynamicTemplate)).properties(propertiesMap).source(sourceBuilder -> sourceBuilder.enabled(true)))
                            .settings(indexSettings)
                            .aliases(aliasesKey, Alias.of(aliasBuilder -> aliasBuilder))));
            // Send the template creation request
            final PutIndexTemplateResponse putIndexTemplateResponse = superUserClient.indices().putIndexTemplate(templateRequest);
            if (Boolean.TRUE.equals(putIndexTemplateResponse.acknowledged())) {
                log.info("template {} is successfully created", templateName);
                removeSupersededLegacyTemplate(templateName);
            } else {
                log.error("An error occurred while creating template \'{}\'. Error: {}", templateName, putIndexTemplateResponse);
            }
            createTemplateIndex(indexName);
        } catch (IOException e) {
            log.error("Error while creating template \'{}\'", templateName, e);
            throw new RuntimeException(e);
        }
    }

    private void createTemplateIndex(String indexName) {
        try {
            final String index = RollingIndexUtil.getRollingIndexName(indexName, LocalDateTime.now());
            if (missingIndex(index)) {
                CreateIndexRequest createIndexRequest = CreateIndexRequest.of(builder -> builder.index(index));
                CreateIndexResponse createIndexResponse = superUserClient.indices().create(createIndexRequest);
                if (Boolean.TRUE.equals(createIndexResponse.acknowledged())) {
                    log.info("index {} is successfully created", indexName);
                } else {
                    log.error("Error: {}", createIndexResponse);
                }
            } else {
                log.info("Index \'{}\' already exists, skipping index creation.", index);
            }
        } catch (IOException e) {
            log.error("Error while creating index: {}", indexName, e);
            throw new RuntimeException(e);
        }
    }

    private static String getDynamicTemplateValue(JsonNode templateNode) {
        String matchMappingType = null;
        JsonNode dynamicTemplatesNode = templateNode.path(MAPPINGS).path(DYNAMIC_TEMPLATES);
        if (dynamicTemplatesNode.isArray()) {
            for (JsonNode node : dynamicTemplatesNode) {
                JsonNode matchMappingTypeNode = node.path(STRINGS_AS_KEYWORDS).path(MATCH_MAPPING_TYPE);
                if (!matchMappingTypeNode.isMissingNode()) {
                    matchMappingType = matchMappingTypeNode.asText();
                }
            }
        }
        return matchMappingType;
    }

    private boolean missingIndex(String indexName) {
        try {
            return !superUserClient.indices().exists(ExistsRequest.of(builder -> builder.index(List.of(indexName)))).value();
        } catch (IOException e) {
            log.error("ERROR while checking the existing index : {}", indexName, e);
        }
        return true;
    }

    private Map<String, Property> createIndexProperties(String filePath) {
        final Map<String, Property> propertiesMap = new HashMap<>();
        try {
            final InputStream inputStream = getClass().getResourceAsStream(filePath);
            if (inputStream == null) {
                throw new IOException("File not found");
            }
            final JsonNode rootNode = mapper.readTree(inputStream);
            final JsonNode propertiesNode = rootNode.path(MAPPINGS).path(PROPERTIES);
            parseProperties(propertiesNode, propertiesMap);
            inputStream.close();
        } catch (IOException e) {
            log.error("Error while creating a index properties", e);
        }
        return propertiesMap;
    }

    void parseProperties(JsonNode propertiesNode, Map<String, Property> propertiesMap) {
        propertiesNode.fields().forEachRemaining(entry -> {
            final String propName = entry.getKey();
            final JsonNode propInfo = entry.getValue();
            final Map<String, Property> nestedProperties = new HashMap<>();
            if (propInfo.has(TYPE) && (propInfo.get(TYPE).asText().equalsIgnoreCase("object") || propInfo.get(TYPE).asText().equalsIgnoreCase("nested"))) {
                if (propInfo.has(PROPERTIES)) {
                    parseProperties(propInfo.path(PROPERTIES), nestedProperties);
                }
            }
            final String propType = propInfo.path(TYPE).asText();
            final boolean storeValue = propInfo.has(STORE) && propInfo.get(STORE).asBoolean();
            // "enabled": false keeps an object out of the mapping (userExtensions.objectValue):
            // without it every key below gets a dynamic mapping, and one document with 15 and
            // 1.5 under the same key fails in a fresh index.
            final Boolean enabled = propInfo.has(ENABLED) ? propInfo.get(ENABLED).asBoolean() : null;
            final Property property = createProperty(Objects.requireNonNull(PropertyTypeEnum.fromTypeString(propType)), storeValue, nestedProperties, enabled);
            if (!Objects.isNull(property)) {
                propertiesMap.put(propName, property);
            }
        });
    }

    private Property createProperty(PropertyTypeEnum propertyType, Boolean storeValue, Map<String, Property> nestedProperties, Boolean enabled) {
        return switch (propertyType) {
            case KEYWORD -> Property.of(builder -> builder.keyword(KeywordProperty.of(keywordBuilder -> keywordBuilder.store(storeValue))));
            case DATE -> Property.of(builder -> builder.date(DateProperty.of(dateBuilder -> dateBuilder.store(storeValue))));
            case OBJECT -> Property.of(builder -> builder.object(ObjectProperty.of(objectBuilder -> {
                if (enabled != null) {
                    objectBuilder.enabled(enabled);
                }
                if (nestedProperties != null && !nestedProperties.isEmpty()) {
                    objectBuilder.properties(nestedProperties);
                }
                return objectBuilder;
            })));
            case NESTED -> Property.of(builder -> builder.nested(nestedProperty -> {
                if (nestedProperties != null && !nestedProperties.isEmpty()) {
                    nestedProperty.properties(nestedProperties);
                }
                return nestedProperty;
            }));
            case BOOLEAN -> Property.of(builder -> builder.boolean_(BooleanProperty.of(booleanBuilder -> booleanBuilder.store(storeValue))));
            case LONG -> Property.of(builder -> builder.long_(LongNumberProperty.of(longBuilder -> longBuilder.store(storeValue))));
            case DOUBLE -> Property.of(builder -> builder.double_(DoubleNumberProperty.of(doubleBuilder -> doubleBuilder.store(storeValue))));
            case FLOAT -> Property.of(builder -> builder.float_(FloatNumberProperty.of(floatBuilder -> floatBuilder.store(storeValue))));
            case PERCOLATOR -> Property.of(builder -> builder.percolator(PercolatorProperty.of(percolatorBuilder -> percolatorBuilder)));
            case TEXT -> Property.of(builder -> builder.text(TextProperty.of(textBuilder -> textBuilder.store(storeValue))));
        };
    }

    private String getConfigProperty(String name) {
        return switch (name) {
            case "user-extension-fields" -> openSearchConfig.indices().userExtensionFields();
            case "user-extension-schema" -> openSearchConfig.indices().userExtensionSchema();
            case "epcis-query-template" -> openSearchConfig.templates().epcisQuery();
            case "epcis-subscription-template" -> openSearchConfig.templates().epcisSubscription();
            case "epcis-percolator-queries-template" -> openSearchConfig.templates().epcisPercolatorQueries();
            case "capture-job-template" -> openSearchConfig.templates().captureJob();
            case "epcis-event-template" -> openSearchConfig.templates().epcisEvent();
            case "epc-template" -> openSearchConfig.templates().epc();
            default -> throw new IllegalArgumentException("Unknown index or template name: " + name);
        };
    }

    public OpenSearchInitializer(OpenSearchClient superUserClient, ObjectMapper mapper, OpenSearchConfig openSearchConfig) {
        this.superUserClient = superUserClient;
        this.mapper = mapper;
        this.openSearchConfig = openSearchConfig;
    }
}
