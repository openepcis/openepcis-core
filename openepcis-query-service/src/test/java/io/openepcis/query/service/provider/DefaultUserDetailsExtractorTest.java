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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openepcis.repository.config.RepositoryConfiguration;
import io.openepcis.service.util.Constants;
import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.inject.Instance;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The single-tenant extractor takes the group from configuration and never from a token, so a
 * Community Edition cannot be talked into another tenant's indices by a crafted claim.
 */
class DefaultUserDetailsExtractorTest {

  private final RepositoryConfiguration configuration = mock(RepositoryConfiguration.class);
  private final RepositoryConfiguration.User user = mock(RepositoryConfiguration.User.class);
  private final SecurityIdentity identity = mock(SecurityIdentity.class);
  private final Principal principal = mock(Principal.class);
  private DefaultUserDetailsExtractor subject;

  @BeforeEach
  void setUp() {
    when(configuration.user()).thenReturn(user);
    when(user.defaultGroup()).thenReturn("query");
    when(user.defaultUser()).thenReturn("openepcis-admin");
    when(user.usernameIsDefaultGroup()).thenReturn(false);
    when(identity.getPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn("alice");
    subject = new DefaultUserDetailsExtractor(configuration);
  }

  @Test
  @DisplayName("the group is the configured one, whatever the token claims")
  void groupFromConfiguration() {
    when(identity.getCredential(TokenCredential.class))
        .thenReturn(new TokenCredential(jwtWithGroup("acme"), "bearer"));
    assertEquals("query", subject.getDefaultGroup(identity));
  }

  @Test
  @DisplayName("the user name becomes the group when configuration says so")
  void groupFromUserName() {
    when(user.usernameIsDefaultGroup()).thenReturn(true);
    assertEquals("alice", subject.getDefaultGroup(identity));
  }

  @Test
  @DisplayName("the user id is the principal, or the ID attribute an authentication set")
  void userId() {
    assertEquals("alice", subject.getUserId(identity));
    when(identity.getAttribute(Constants.CAPITAL_ID)).thenReturn("user-42");
    assertEquals("user-42", subject.getUserId(identity));
  }

  @Test
  @DisplayName("without a caller the configured defaults answer")
  void unresolvableInstance() {
    @SuppressWarnings("unchecked")
    final Instance<SecurityIdentity> none = mock(Instance.class);
    when(none.isResolvable()).thenReturn(false);
    assertEquals("openepcis-admin", subject.getUserId(none));
    assertEquals("query", subject.getDefaultGroup(none));
  }

  @Test
  @DisplayName("an anonymous caller counts as no caller")
  void anonymousInstance() {
    @SuppressWarnings("unchecked")
    final Instance<SecurityIdentity> anonymous = mock(Instance.class);
    when(anonymous.isResolvable()).thenReturn(true);
    when(anonymous.get()).thenReturn(identity);
    when(identity.isAnonymous()).thenReturn(true);
    assertEquals("openepcis-admin", subject.getUserId(anonymous));
  }

  private static String jwtWithGroup(final String group) {
    final String payload =
        java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(("{\"defaultGroup\":\"" + group + "\"}").getBytes());
    return "eyJhbGciOiJub25lIn0." + payload + ".sig";
  }
}
