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
package io.openepcis.query.service.util;

import io.openepcis.model.epcis.OrderByUserExtension;
import io.openepcis.model.epcis.exception.QueryParameterException;
import io.openepcis.repository.cache.UserExtensionsCache;
import io.openepcis.repository.util.EventConvertor;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class OrderByUtil {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderByUtil.class);
  private final UserExtensionsCache userExtensionsCache;
  private static final Set<String> INVALID_DATA_TYPE_FOR_SORTING = Set.of("boolean", "object");

  public Uni<OrderByUserExtension> buildOrderByUX(Map<String, Object> context, String orderBy) {
    final String key = orderBy.substring(orderBy.lastIndexOf(".") + 1);
    final String expandedKey = EventConvertor.getExpandedKeyForUserExtension(context, key.split(":"));
    return userExtensionsCache.getType(expandedKey).map(dataType -> {
      if (INVALID_DATA_TYPE_FOR_SORTING.contains(dataType)) {
        log.error(String.format("The extension field of type %s is not allowed in orderBy clause", dataType));
        throw new QueryParameterException(String.format("The extension field of type %s is not allowed in orderBy clause", dataType));
      }
      final String path = EventConvertor.getExpandedPathForUserExtension(context, orderBy);
      final String nested = getNestedPath(orderBy);
      final OrderByUserExtension orderByUX = new OrderByUserExtension();
      orderByUX.setDataType(dataType);
      orderByUX.setNestedPath(nested);
      orderByUX.setPath(path);
      orderByUX.setExtendedKey(expandedKey);
      return orderByUX;
    }).onFailure().recoverWithUni(throwable -> {
      log.error("Error while preparing a orderBy details for userExtension: {}", throwable.getMessage());
      return Uni.createFrom().failure(throwable);
    });
  }

  private String getNestedPath(String orderBy) {
    final String nested;
    if (orderBy.length() - orderBy.replaceAll("\\.", "").length() == 1) {
      if (orderBy.startsWith("ilmd")) {
        nested = "ilmd.userExtensions";
      } else if (orderBy.startsWith("readPoint")) {
        nested = "readPoint.userExtensions";
      } else if (orderBy.startsWith("bizLocation")) {
        nested = "bizLocation.userExtensions";
      } else if (orderBy.startsWith("errorDeclaration")) {
        nested = "errorDeclaration.userExtensions";
      } else if (orderBy.startsWith("sensorElementList")) {
        nested = "sensorElementList.userExtensions";
      } else if (orderBy.startsWith("sensorElementList.sensorMetadata")) {
        nested = "sensorElementList.sensorMetadata.userExtensions";
      } else if (orderBy.startsWith("sensorElementList.sensorReport")) {
        nested = "sensorElementList.sensorReport.userExtensions";
      } else {
        nested = "userExtensions";
      }
    } else {
      if (orderBy.startsWith("ilmd")) {
        nested = "ilmd.innerUserExtensions";
      } else if (orderBy.startsWith("readPoint")) {
        nested = "readPoint.innerUserExtensions";
      } else if (orderBy.startsWith("bizLocation")) {
        nested = "bizLocation.innerUserExtensions";
      } else if (orderBy.startsWith("errorDeclaration")) {
        nested = "errorDeclaration.innerUserExtensions";
      } else if (orderBy.startsWith("sensorElementList")) {
        nested = "sensorElementList.innerUserExtensions";
      } else if (orderBy.startsWith("sensorElementList.sensorMetadata")) {
        nested = "sensorElementList.sensorMetadata.innerUserExtensions";
      } else if (orderBy.startsWith("sensorElementList.sensorReport")) {
        nested = "sensorElementList.sensorReport.innerUserExtensions";
      } else {
        nested = "innerUserExtensions";
      }
    }
    return nested;
  }

  public OrderByUtil(UserExtensionsCache userExtensionsCache) {
    this.userExtensionsCache = userExtensionsCache;
  }
}
