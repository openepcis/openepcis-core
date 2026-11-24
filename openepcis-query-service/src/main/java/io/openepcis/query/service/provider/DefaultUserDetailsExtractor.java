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
package io.openepcis.query.service.provider;

import io.openepcis.repository.config.RepositoryConfiguration;
import io.openepcis.service.api.UserDetailsExtractor;
import io.openepcis.service.util.Constants;
import io.quarkus.arc.DefaultBean;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * The single-tenant reading of who is calling, as the Community Edition ships it. The group every
 * record is written under comes from configuration alone ({@code repository.user.default-group},
 * or the user name when {@code repository.user.username-is-default-group} is set); token claims are
 * never consulted. The user id is the principal name, unless an authentication mechanism has set
 * the {@link Constants#CAPITAL_ID} attribute.
 *
 * <p>Being the {@code @DefaultBean}, this bean steps aside as soon as another {@link
 * UserDetailsExtractor} is on the classpath.
 */
@DefaultBean
@ApplicationScoped
public class DefaultUserDetailsExtractor implements UserDetailsExtractor {

  private final RepositoryConfiguration repositoryConfiguration;

  @Inject
  public DefaultUserDetailsExtractor(final RepositoryConfiguration repositoryConfiguration) {
    this.repositoryConfiguration = repositoryConfiguration;
  }

  @Override
  public String getUserId(final SecurityIdentity securityIdentity) {
    final Object id = securityIdentity.getAttribute(Constants.CAPITAL_ID);
    if (id != null) {
      return id.toString();
    }
    return securityIdentity.getPrincipal().getName();
  }

  @Override
  public String getDefaultGroup(final SecurityIdentity securityIdentity) {
    if (repositoryConfiguration.user().usernameIsDefaultGroup()) {
      return securityIdentity.getPrincipal().getName();
    }
    return repositoryConfiguration.user().defaultGroup();
  }

  @Override
  public String getUserId(final Instance<SecurityIdentity> securityIdentity) {
    if (securityIdentity.isResolvable() && !securityIdentity.get().isAnonymous()) {
      return getUserId(securityIdentity.get());
    }
    return repositoryConfiguration.user().defaultUser();
  }

  @Override
  public String getDefaultGroup(final Instance<SecurityIdentity> securityIdentity) {
    if (securityIdentity.isResolvable() && !securityIdentity.get().isAnonymous()) {
      return getDefaultGroup(securityIdentity.get());
    }
    return repositoryConfiguration.user().defaultGroup();
  }
}
