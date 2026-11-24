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
package io.openepcis.repository.opensearch;

import io.openepcis.core.exception.ValidationException;
import io.openepcis.model.dto.CaptureJob;
import io.openepcis.model.dto.InvalidEPCISEventInfo;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.UserExtensionField;
import io.openepcis.model.epcis.exception.CaptureValidationException;
import io.openepcis.model.epcis.exception.PersistenceException;
import io.openepcis.model.epcis.exception.QueryExecutionException;
import io.openepcis.opentelemetry.logging.OpenEPCISLogger;
import io.openepcis.repository.Constants;
import io.openepcis.repository.api.EventRepository;
import io.openepcis.repository.config.RepositoryConfiguration;
import io.openepcis.repository.model.CaptureJobES;
import io.openepcis.repository.model.EPCISEventES;
import io.openepcis.repository.util.RollingIndexUtil;
import io.openepcis.validation.service.ValidationMessageFormatter;
import io.quarkiverse.opensearch.OpenSearchClientName;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.collections4.CollectionUtils;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermsQueryField;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.UpdateByQueryRequest;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.bulk.CreateOperation;
import io.openepcis.model.epcis.constants.CommonConstants;
import io.openepcis.repository.model.CapturedByES;
import io.openepcis.repository.util.EventDocuments;
import io.smallrye.mutiny.Multi;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static io.openepcis.model.epcis.exception.ExceptionMessages.*;
import static io.openepcis.repository.util.EventConvertor.getESRepresentation;
import static io.openepcis.repository.util.InvalidEventInfoUtil.condenseInvalidInfos;

@ApplicationScoped
public class EventRepositoryImpl implements EventRepository {
  private final RepositoryConfiguration configService;
  private final QueryRepositoryImpl queryRepository;
  // Named "admin" client for write operations (capture/indexing)
  // Uses Basic auth credentials, not affected by DLS
  @Inject
  @OpenSearchClientName("admin")
  OpenSearchAsyncClient superUserAsyncClient;

  /**
   * The refresh policy of the capture job record: searchable once written, for the search that
   * lists capture jobs. {@code GET /capture/{id}} reads the job by id and does not need it.
   *
   * <p>Events are no longer written with it. A wait per event held a persistence worker for up to
   * a refresh interval; the job now completes its events by document id (markVisible), without waiting,
   * and only its search fallback refreshes first.
   *
   * <p>WaitFor rather than True: it rides the next scheduled refresh instead of forcing one.
   */
  static final Refresh WRITE_REFRESH = Refresh.WaitFor;
  private static final OpenEPCISLogger log = OpenEPCISLogger.getLogger(EventRepositoryImpl.class);

  @Override
  public Uni<String> saveCaptureJob(CaptureJob captureJob, String captureID, OffsetDateTime recordDate, String defaultGroup, Map<String, Object> metadata) {
    if (captureJob == null) {
      return Uni.createFrom().failure(new CaptureValidationException(EMPTY_CAPTURE_BODY_CANNOT_BE_SAVED));
    }
    try {
      final CaptureJobES captureJobES = new CaptureJobES(captureJob, metadata);
      // See WRITE_REFRESH. Here it decides whether GET /capture/{id} can answer at
      // all: an outbox asks a second after submitting, and a 404 reads as "refused".
      final IndexRequest<CaptureJobES> indexRequest = new IndexRequest.Builder<CaptureJobES>().index(RollingIndexUtil.getRollingIndexName(configService.capture().job().index(), recordDate, defaultGroup)).id(captureID).document(captureJobES).refresh(WRITE_REFRESH).build();
      return Uni.createFrom().completionStage(superUserAsyncClient.index(indexRequest)).map(indexResponse -> {
        log.debug("indexed: {} with id : {} ", indexRequest.index(), indexResponse.id());
        return indexResponse.id();
      }).onFailure().recoverWithUni(throwable -> Uni.createFrom().failure(new PersistenceException(ERROR_WHILE_PERSISTING_EPCIS_DOCUMENT, throwable)));
    } catch (Exception t) {
      return Uni.createFrom().failure(t);
    }
  }

  @Override
  public Uni<Boolean> bulkSaveOrUpdate(List<EPCISEvent> epcisEvents, List<InvalidEPCISEventInfo> invalidEventsInfo, boolean isProceed, String captureID, ZonedDateTime docCreationDate, Map<String, Object> metadata, String defaultGroup, List<Object> context, boolean refresh) {
    try {
      final List<CreateOperation<EPCISEventES>> bulkRequests = new ArrayList<>();
      // What each create carried, by document id: a conflict is settled against it.
      final Map<String, EPCISEventES> written = new HashMap<>();
      final List<Integer> invalidIndexes = new ArrayList<>();
      final Set<String> errorMsgs = new HashSet<>();
      // Generate the ES representation for each event and handle any exception that may occur
      // during the process
      for (EPCISEvent c : epcisEvents) {
        EPCISEventES eventES = null;
        try {
          eventES = getESRepresentation(c, metadata, context);
        } catch (ValidationException e) {
          invalidIndexes.add(c.getOpenEPCISExtension().getSequenceInEPCISDoc());
          if (isProceed) {
            errorMsgs.add(e.getLocalizedMessage());
          } else {
            invalidEventsInfo.add(new InvalidEPCISEventInfo("InvalidEPCException", "Translation failed", 400, e.getLocalizedMessage(), invalidIndexes));
            queryRepository.updateCaptureBehaviour(captureID, false, false, condenseInvalidInfos(invalidEventsInfo), docCreationDate, defaultGroup);
            return Uni.createFrom().item(false);
          }
        }
        // If the ES representation successful
        if (Objects.nonNull(eventES)) {
          if (isProceed) {
            eventES.getMetadata().setVisible(true);
          }
          // Deterministic place and id (EventDocuments): op_type create refuses a second copy of
          // the same content, from this tenant or any other, in the one index it can be in.
          final String hash = eventES.getHash();
          final CreateOperation.Builder<EPCISEventES> create = new CreateOperation.Builder<EPCISEventES>().index(eventIndexName(c.getEventTime())).document(eventES);
          if (hash != null && !hash.isBlank()) {
            final String documentId = EventDocuments.documentId(hash, c.getErrorDeclaration() != null);
            create.id(documentId);
            written.put(documentId, eventES);
          }
          bulkRequests.add(create.build());
        }
        // If any invalid info present
        if (!invalidIndexes.isEmpty()) invalidEventsInfo.add(new InvalidEPCISEventInfo("InvalidEPCException", "Translation failed", 400, ValidationMessageFormatter.format(errorMsgs), invalidIndexes));
      }
      if (bulkRequests.size() > 0 || (!isProceed && epcisEvents.size() == bulkRequests.size())) {
        final BulkRequest.Builder request = new BulkRequest.Builder();
        bulkRequests.forEach(r -> request.operations(o -> o.create(r)));
        // No refresh policy here. Waiting for a refresh per event held a persistence worker for up
        // to a refresh interval, which capped capture at a few events per second. Nothing searches
        // for these documents any more: the capture job completes them by id (markVisible), and
        // only its fallback for large jobs refreshes once before it searches.
        final BulkRequest bulkRequest = request.build();
        return Uni.createFrom().completionStage(superUserAsyncClient.bulk(bulkRequest)).chain(bulkResponse -> {
          if (bulkResponse.errors()) {
            final List<BulkResponseItem> failed = bulkResponse.items().stream().filter(i -> i.error() != null && i.status() != 409).toList();
            if (!failed.isEmpty()) {
              invalidEventsInfo.add(new InvalidEPCISEventInfo("InvalidEPCException", "Translation failed", 400, ValidationMessageFormatter.format(errorMsgs), invalidIndexes));
              throw new PersistenceException(ERROR_WHILE_PERSISTING_EVENT + failed.stream().map(item -> item.error().reason()).toList());
            }
            // Only conflicts: the content is stored already. Settle each one, then carry on.
            final List<BulkResponseItem> conflicts = bulkResponse.items().stream().filter(i -> i.status() == 409).toList();
            return Multi.createFrom().iterable(conflicts).onItem().transformToUniAndConcatenate(item -> settleConflict(item, written.get(item.id()), metadata, captureID, defaultGroup, invalidEventsInfo)).collect().asList().replaceWith(true);
          }
          log.debug("Events indexed: {}   ", configService.epcis().event().index());
          if (!refresh) {
            return Uni.createFrom().item(true);
          }
          return Uni.createFrom().completionStage(() -> {
            try {
              // Only the indices just written to, not every event index of every tenant.
              final List<String> writtenIndices = bulkRequests.stream().map(CreateOperation::index).filter(Objects::nonNull).distinct().toList();
              return superUserAsyncClient.indices().refresh(r -> r.index(writtenIndices.isEmpty() ? List.of(allEventIndices()) : writtenIndices));
            } catch (IOException e) {
              throw new RuntimeException("Index refresh failed", e);
            }
          }).replaceWith(true).onFailure().retry().withBackOff(Duration.ofSeconds(1)).withJitter(0.2).atMost(5).onFailure().recoverWithItem(t -> {
            log.warn("Index refresh failed after retries, event may not be immediately searchable", t);
            return true;
          });
        }).onFailure().invoke(t -> {
          invalidEventsInfo.add(new InvalidEPCISEventInfo("InvalidEPCException", "Translation failed", 400, ValidationMessageFormatter.format(errorMsgs), invalidIndexes));
          throw new PersistenceException(ERROR_WHILE_PERSISTING_EPCIS_DOCUMENT, t);
        });
      } else {
        return Uni.createFrom().item(Boolean.FALSE);
      }
    } catch (Exception exception) {
      return Uni.createFrom().failure(exception);
    }
  }

  @Override
  public String eventIndexName(final OffsetDateTime eventTime) {
    return EventDocuments.indexName(configService.epcis().event().index(), eventTime);
  }

  /**
   * A create that found the document there already: the same content is stored.
   *
   * <ul>
   *   <li>Written by this capture itself, at the same place in the document, or this capture is
   *       in {@code capturedBy} already: the retry of a write that went through. Saved, nothing to
   *       add.
   *   <li>Written by this capture at another place in the document: the document holds the same
   *       event twice. That stays the duplicate it always was.
   *   <li>Captured before by the same tenant: an idempotent replay. It counts as saved, the
   *       document is not written again, and this capture is added to {@code capturedBy}, so the
   *       capture job finds as many events under its captureID as it processed. The metadata gets
   *       {@link Constants#IS_REPLAY}: the caller sends no event-saved for it.
   *   <li>Captured by another tenant: that tenant is added to {@code capturedBy} of the one shared
   *       document, as the duplicate path always did.
   * </ul>
   *
   * <p>An error declaration is settled the same way when it says the same as the stored one
   * ({@link EventDocuments#sameDeclaration}); a different one is refused, there is one per event.
   */
  Uni<Void> settleConflict(final BulkResponseItem item, final EPCISEventES attempted, final Map<String, Object> metadata, final String captureID, final String defaultGroup, final List<InvalidEPCISEventInfo> invalidEventsInfo) {
    final String id = item.id();
    final boolean declaration = id != null && id.endsWith(EventDocuments.DECLARATION_QUALIFIER);
    try {
      return Uni.createFrom().completionStage(superUserAsyncClient.get(g -> g.index(item.index()).id(id), EPCISEventES.class)).chain(found -> {
        final EPCISEventES stored = found.source();
        if (!found.found() || stored == null) {
          return Uni.createFrom().failure(new PersistenceException("Create conflict on " + id + " in " + item.index() + ", but the document is not there"));
        }
        if (declaration && (attempted == null || !EventDocuments.sameDeclaration(stored.getErrorDeclarationES(), attempted.getErrorDeclarationES()))) {
          final String detail = "The event already has an error declaration (" + id + "), and it says something else. EPCIS keeps one per event; to correct it, capture a corrective event.";
          invalidEventsInfo.add(new InvalidEPCISEventInfo("CaptureValidationException", "Validation failed", 400, detail, List.of()));
          return Uni.createFrom().failure(new PersistenceException(detail));
        }
        if (captureID != null && captureID.equals(stored.getCaptureID())) {
          final Integer sequence = attempted == null ? null : attempted.getSequenceInEPCISDoc();
          if (sequence != null && stored.getSequenceInEPCISDoc() != null && !sequence.equals(stored.getSequenceInEPCISDoc())) {
            invalidEventsInfo.add(new InvalidEPCISEventInfo("CaptureValidationException", "Validation failed", 400, "Duplicate EPCIS Event: the document holds this event twice", List.of(sequence)));
            return Uni.createFrom().failure(new PersistenceException("Duplicate EPCIS Event within capture " + captureID + ": " + id));
          }
          return Uni.createFrom().voidItem();
        }
        if (EventDocuments.capturedIn(stored, captureID)) {
          // a retry after this capture was added already
          if (!EventDocuments.newInCapture(stored, captureID)) {
            markReplay(metadata);
          }
          return Uni.createFrom().voidItem();
        }
        final CapturedByES capturer = metadata == null || metadata.get(CommonConstants.CAPTURED_BY) == null ? null : OBJECT_MAPPER.convertValue(metadata.get(CommonConstants.CAPTURED_BY), CapturedByES.class);
        final boolean sameTenant = capturer == null || EventDocuments.capturedByTenant(stored, defaultGroup != null ? defaultGroup : capturer.getDefaultGroup(), capturer.getUserID());
        if (sameTenant) {
          markReplay(metadata);
        }
        if (capturer == null) {
          return Uni.createFrom().voidItem();
        }
        capturer.setCaptureID(captureID);
        capturer.setTimestamp(OffsetDateTime.now());
        if (stored.getMetadata() == null) {
          stored.setMetadata(new io.openepcis.repository.model.MetadataES());
        }
        if (stored.getMetadata().getCapturedBy() == null) {
          stored.getMetadata().setCapturedBy(new ArrayList<>());
        } else {
          stored.getMetadata().setCapturedBy(new ArrayList<>(stored.getMetadata().getCapturedBy()));
        }
        stored.getMetadata().getCapturedBy().add(capturer);
        try {
          return Uni.createFrom().completionStage(superUserAsyncClient.update(u -> u.index(item.index()).id(id).doc(stored), EPCISEventES.class)).replaceWithVoid();
        } catch (IOException e) {
          return Uni.createFrom().failure(new PersistenceException("Error while adding a capturer to " + id, e));
        }
      });
    } catch (IOException e) {
      return Uni.createFrom().failure(new PersistenceException("Error while reading " + id, e));
    }
  }

  private static void markReplay(final Map<String, Object> metadata) {
    if (metadata == null) {
      return;
    }
    try {
      metadata.put(io.openepcis.repository.Constants.IS_REPLAY, Boolean.TRUE);
    } catch (UnsupportedOperationException e) {
      // an immutable map: the caller cannot be told, and sends event-saved as for a new event
    }
  }

  private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

  @Override
  public Uni<Boolean> bulkSaveUserExtensionFields(Set<UserExtensionField> userExtensions) {
    try {
      final BulkRequest.Builder request = new BulkRequest.Builder();
      for (UserExtensionField userExtension : userExtensions) {
        request.operations(operation -> operation.index(idx -> idx.index(configService.user().extension().fields().index()).document(userExtension)));
      }
      return Uni.createFrom().completionStage(superUserAsyncClient.bulk(request.build())).map(bulkResponse -> {
        log.debug("User Extension fields indexed: {}   ", configService.user().extension().fields().index());
        if (bulkResponse.errors()) {
          log.error("Bulk had errors");
          bulkResponse.items().stream().filter(item -> item.error() != null).forEach(item -> log.error(ERROR_WHILE_PERSISTING_USER_EXTENSION_FIELDS + item.error().reason()));
          throw new PersistenceException(ERROR_WHILE_PERSISTING_USER_EXTENSION_FIELDS + Arrays.toString(bulkResponse.items().stream().filter(item -> item.error() != null).map(item -> item.error().reason()).toArray()));
        } else {
          return true;
        }
      }).onFailure().invoke(t -> {
        throw new PersistenceException(ERROR_WHILE_PERSISTING_EPCIS_DOCUMENT, t);
      });
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Uni<Boolean> deleteEventsFromEventID(String eventID) {
    final DeleteByQueryRequest request = new DeleteByQueryRequest.Builder().index(configService.epcis().event().index() + "-*").conflicts(Conflicts.Proceed).query(q -> q.term(t -> t.field(Constants.EVENT_ID).value(FieldValue.of(eventID)))).build();
    return executeDeleteByQuery(request);
  }

  @Override
  public Uni<Boolean> removeEventsForCaptureID(String captureID, ZonedDateTime createdAt, String defaultGroup) {
    final org.opensearch.client.opensearch.core.DeleteByQueryRequest request = new DeleteByQueryRequest.Builder().index(allEventIndices()).allowNoIndices(true).ignoreUnavailable(true).conflicts(Conflicts.Proceed).scrollSize(100).refresh(Refresh.True).query(q -> q.term(t -> t.field(Constants.CAPTURE_ID).value(FieldValue.of(captureID)))).build();
    // Captures write without waiting for a refresh, so make the job's events searchable first:
    // delete by query only sees what a search sees. Rollbacks are rare; this is their cost.
    return refreshEventIndices().onFailure().recoverWithNull().replaceWith(Boolean.TRUE).chain(() -> executeDeleteByQuery(request)).onItem().transformToUni(result -> {
      if (result) {
        return updateMetaDataForCaptureID(captureID);
      }
      return Uni.createFrom().item(Boolean.FALSE);
    }).onFailure().recoverWithUni(throwable -> Uni.createFrom().failure(throwable));
  }

  private Uni<Boolean> updateMetaDataForCaptureID(String captureID) {
    // Build the UpdateByQueryRequest with the appropriate query and inline script
    UpdateByQueryRequest updateRequest =  // Replace with the correct index name
    // Specify the path to the nested array
    // Search
    // for
    // captureID in the array
    // Convert captureID to JsonData
    new UpdateByQueryRequest.Builder().index(configService.epcis().event().index()).query(q -> q.nested(n -> n.path("metadata").query(nq -> nq.term(t -> t.field("metadata.capturedBy.captureID").value(FieldValue.of(captureID)))))).script(s -> s.inline(i -> i.source("for (int i = 0; i < ctx._source.metadata.capturedBy.size(); i++) { " + "if (ctx._source.metadata.capturedBy[i].captureID == params.captureID) { " + "ctx._source.metadata.capturedBy.remove(i); break; } }").params(Collections.singletonMap("captureID", JsonData.of(captureID))))).build();
    // Execute the update query using your OpenSearch client
    return 
    // If the update was successful, return true
    // If the update was not successful, return false
    executeUpdateByQuery(updateRequest).onItem().transform(result -> {
      if (result) {
        return Boolean.TRUE;
      }
      return Boolean.FALSE;
    }).onFailure().recoverWithItem(Boolean.FALSE); // In case of failure, return false
  }

  private Uni<Boolean> executeUpdateByQuery(UpdateByQueryRequest updateRequest) {
    // Example implementation for executing the update request using OpenSearch client
    return  // Replace with the actual OpenSearch client call
    // Check response for success (e.g., number of updated documents)
    Uni.createFrom().completionStage(() -> {
      try {
        return superUserAsyncClient.updateByQuery(updateRequest);
      } catch (IOException e) {
        throw new QueryExecutionException("Error while updating the event metadata", e);
      }
    }).onItem().transform(response -> {
      if (response.updated() > 0) {
        return Boolean.TRUE;
      } else {
        return Boolean.FALSE;
      }
    }).onFailure().recoverWithItem(Boolean.FALSE);
  }

  private Uni<Boolean> executeDeleteByQuery(DeleteByQueryRequest request) {
    try {
      return Uni.createFrom().completionStage(superUserAsyncClient.deleteByQuery(request)).map(deleteByQueryResponse -> {
        log.debug("Total invalidEvents proceed to delete are: {}", deleteByQueryResponse.total());
        log.debug("Total invalidEvents deleted are: {}", deleteByQueryResponse.deleted());
        return CollectionUtils.isEmpty(deleteByQueryResponse.failures());
      }).onFailure().recoverWithUni(throwable -> Uni.createFrom().failure(throwable));
    } catch (IOException e) {
      return Uni.createFrom().failure(e);
    }
  }

  public Uni<Boolean> updateVisibilityByCaptureID(String captureID, ZonedDateTime createdAt, String defaultGroup) {
    try {
      TermsQueryField userIdTermsQuery = new TermsQueryField.Builder().value(Collections.singletonList(FieldValue.of(captureID))).build();
      Query captureIdQuery = new Query.Builder().nested(n -> n.path("metadata").query(q -> q.terms(t -> t.field("metadata.capturedBy.captureID").terms(userIdTermsQuery)))).build();
      // A job's events are in the month indices of their eventTimes (EventDocuments), and older
      // ones in the tenant's index of the job's month; the captureID finds them in either. The
      // caller refreshed the event indices before it counted them (refreshEventIndices).
      final String indexName = allEventIndices();
      UpdateByQueryRequest updateByQueryRequest = new UpdateByQueryRequest.Builder().index(indexName).conflicts(Conflicts.Proceed).refresh(Refresh.True).query(captureIdQuery).script(s -> s.inline(script -> script.lang(sl -> sl.builtin(org.opensearch.client.opensearch._types.BuiltinScriptLanguage.Painless)).source("ctx._source.metadata.visible = true"))).build();
      return Uni.createFrom().completionStage(() -> {
        try {
          return superUserAsyncClient.updateByQuery(updateByQueryRequest);
        } catch (IOException e) {
          throw new PersistenceException(e.getMessage(), e);
        }
      }).map(updateByQueryResponse -> {
        log.debug("captured events made visible: {} of {}", updateByQueryResponse.updated(), updateByQueryResponse.total());
        return CollectionUtils.isEmpty(updateByQueryResponse.failures());
      }).onFailure().recoverWithItem(throwable -> {
        log.error("Error while updating visibility of events with captureID {}", captureID, throwable);
        return Boolean.FALSE;
      });
    } catch (Exception e) {
      log.error("Error while updating visibility of events with captureID {}", captureID);
      return Uni.createFrom().item(Boolean.FALSE);
    }
  }

  private static final Map<String, Object> VISIBLE = Map.of("metadata", Map.of("visible", true));

  @Override
  public Uni<Boolean> markVisible(final List<String> documents) {
    if (documents == null || documents.isEmpty()) {
      return Uni.createFrom().item(Boolean.TRUE);
    }
    final BulkRequest.Builder request = new BulkRequest.Builder();
    for (final String document : documents) {
      final EventDocuments.Location at = EventDocuments.Location.parse(document);
      request.operations(o -> o.update(u -> u.index(at.index()).id(at.id()).document(VISIBLE)));
    }
    // No refresh policy: waiting here held the job status back by up to a refresh interval
    // (measured 7 s on dev). Readers by id see the update at once; a search on a search-idle
    // shard refreshes before it answers.
    return Uni.createFrom().completionStage(() -> {
      try {
        return superUserAsyncClient.bulk(request.build());
      } catch (IOException e) {
        throw new PersistenceException(e.getMessage(), e);
      }
    }).map(response -> {
      if (response.errors()) {
        response.items().stream().filter(i -> i.error() != null).limit(3)
            .forEach(i -> log.error("making {}/{} visible failed: {}", i.index(), i.id(), i.error().reason()));
        return Boolean.FALSE;
      }
      return Boolean.TRUE;
    });
  }

  @Override
  public Uni<Void> refreshEventIndices() {
    return Uni.createFrom().completionStage(() -> {
      try {
        return superUserAsyncClient.indices().refresh(r -> r.index(allEventIndices()).allowNoIndices(true).ignoreUnavailable(true));
      } catch (IOException e) {
        throw new PersistenceException(e.getMessage(), e);
      }
    }).replaceWithVoid();
  }

  private String allEventIndices() {
    return configService.epcis().event().index() + "-*";
  }

  public EventRepositoryImpl(RepositoryConfiguration configService, QueryRepositoryImpl queryRepository) {
    this.configService = configService;
    this.queryRepository = queryRepository;
  }
}
