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
package io.openepcis.service.util;

public class Constants {

  public static final String JSON_LD_USER_EXTENSION_FIELD_SEPARATOR = ":";
  public static final String CONTEXT = "@context";
  public static final String HTTPS_NS_GS_1_ORG_EPCIS_EPCIS_BODY =
      "https://ref.gs1.org/epcis/epcisBody";
  public static final String HTTPS_NS_GS_1_ORG_EPCIS_EVENT_LIST =
      "https://ref.gs1.org/epcis/eventList";
  public static final String JSON_LD_VALUE = "@value";
  public static final String KEY = "key";
  public static final String VALUE = "value";
  public static final String CAPTURE_RESOURCE = "capture/";
  public static final String EVENTS_RESOURCE = "events/";
  public static final String EXPANDED_KEY = "expandedKey";
  public static final String EPCIS_BODY = "epcisBody";
  public static final String EVENT_LIST = "eventList";
  public static final String EPCIS_HEADER = "epcisHeader";
  public static final String EPCIS_VERSION = "EPCIS_Version";
  public static final String EPCIS_FORMAT = "EPCIS_Format";
  public static final String READ_POINT = "readPoint";
  public static final String BIZ_STEP = "bizStep";
  public static final String DISPOSITION = "disposition";
  public static final String BIZ_TRANSACTION_LIST = "bizTransactionList";
  public static final String SOURCE_LIST = "sourceList";
  public static final String DESTINATION_LIST = "destinationList";
  public static final String BIZ_TRANSACTION_LIST_TYPE = "bizTransaction-type";
  public static final String SOURCE_DEST_TYPE = "source-dest-type";
  public static final String SENSOR_REPORT_TYPE = "sensorReport-type";
  public static final String BIZ_LOCATION = "bizLocation";
  public static final String EVENT_ID = "eventID";
  public static final String EVENT = "event";
  public static final String TYPE = "type";
  public static final String ERROR_DECLARATION = "errorDeclaration";
  public static final String ERROR_DECLARATION_REASON = "error-reason";
  public static final String CREATION_DATE = "creationDate";
  public static final String CORRECTIVE_EVENT_IDS = "correctiveEventIDs";
  public static final String REASON = "reason";
  public static final String HASH = "hash";
  public static final String ILMD = "ilmd";
  public static final String EPCIS_DOCUMENT = "EPCISDocument";
  public static final String QUERY_NAME = "name";
  public static final String GS1_EXTENSIONS = "GS1_Extensions";
  public static final String VALIDATE_JSON_EPCIS_EVENT = "validateJsonEPCISEvent";
  public static final String SUCCESS = "success";
  public static final String RUNNING = "running";
  public static final String CAPTURE_ID = "captureID";
  public static final String STORAGE_BUCKET = "storageBucket";
  public static final String STORAGE_KEY = "storageKey";
  public static final String CAPTURE_ERROR_BEHAVIOUR = "captureErrorBehaviour";
  public static final String CAPTURE_ERROR_BEHAVIOUR_PROCEED = "PROCEED";
  public static final String CAPTURE_ERROR_BEHAVIOUR_ROLLBACK = "ROLLBACK";
  public static final String ERROR = "error";
  public static final String RECORD_TIME = "recordTime";
  public static final String ID = "id";
  public static final String CAPITAL_ID = "ID";
  public static final String SCHEMA_VERSION = "schemaVersion";
  public static final String EVENT_COUNT = "eventCount";
  public static final String CREATED_AT = "createdAt";
  public static final String CREATED_BY = "createdBy";
  public static final String UPDATED_AT = "updatedAt";
  public static final String UPDATED_BY = "updatedBy";
  public static final String NAMESPACE = "namespace";
  public static final String CAPTURED_AT = "capturedAt";
  public static final String CAPTURED_BY = "capturedBy";
  public static final String DEFAULT_PREFIX = "defaultPrefix";
  public static final String JSONLD_CONTEXT_URL = "jsonldContextUrl";
  public static final String EVENT_ACCESS = "event-access";
  public static final String QUERY_ROLE = "query";
  public static final String SUB = "sub";
  public static final String USERINFO = "userinfo";
  public static final String USERINFOSTRING = "userInfoString";
  public static final String VISIBLE = "visible";
  public static final String JOB_STATUS_SUCCESS = "SUCCESS";
  public static final String JOB_STATUS_FAILURE = "FAILURE";
  public static final String GE_RECORD_TIME_INTERNAL = "GE_recordTimeInternal";
  public static final String EVENTS = "events";
  public static final String COLLECTION = "collection";
  public static final String TRACE_ID = "traceId";
  public static final String SPAN_ID = "spanId";
  public static final String DEFAULT_GROUP = "defaultGroup";
  public static final String GROUPS = "groups";
  public static final String OID = "oid";

  private Constants() {
    // Bunch of constants so there should be a no need to create object for this class
  }
}
