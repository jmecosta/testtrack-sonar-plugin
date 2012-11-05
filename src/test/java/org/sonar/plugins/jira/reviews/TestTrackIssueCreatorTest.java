/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2009 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.testtrack.reviews;

import com.atlassian.jira.rpc.soap.client.JiraSoapService;
import com.atlassian.jira.rpc.soap.client.RemoteAuthenticationException;
import com.atlassian.jira.rpc.soap.client.RemoteIssue;
import com.atlassian.jira.rpc.soap.client.RemotePermissionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.Settings;
import org.sonar.api.workflow.internal.DefaultReview;
import org.sonar.plugins.testtrack.TestTrackConstants;
import org.sonar.plugins.testtrack.soap.TestTrackSoapSession;

import java.rmi.RemoteException;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestTrackIssueCreatorTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  private TestTrackIssueCreator jiraIssueCreator;
  private DefaultReview review;
  private Settings settings;

  @Before
  public void init() throws Exception {
    review = new DefaultReview();
    review.setReviewId(456L);
    review.setMessage("The Cyclomatic Complexity of this method is 14 which is greater than 10 authorized.");
    review.setSeverity("MINOR");
    review.setRuleName("Wrong identation");

    settings = new Settings();
    settings.appendProperty("sonar.core.serverBaseURL", "http://my.sonar.com");
    settings.appendProperty(TestTrackConstants.SERVER_URL_PROPERTY, "http://my.jira.com");
    settings.appendProperty(TestTrackConstants.SOAP_BASE_URL_PROPERTY, TestTrackConstants.SOAP_BASE_URL_DEF_VALUE);
    settings.appendProperty(TestTrackConstants.USERNAME_PROPERTY, "foo");
    settings.appendProperty(TestTrackConstants.PASSWORD_PROPERTY, "bar");
    settings.appendProperty(TestTrackConstants.JIRA_PROJECT_KEY_PROPERTY, "TEST");

    jiraIssueCreator = new TestTrackIssueCreator();
  }

  @Test
  public void shouldCreateSoapSession() throws Exception {
    TestTrackSoapSession soapSession = jiraIssueCreator.createSoapSession(settings);
    assertThat(soapSession.getWebServiceUrl().toString(), is("http://my.jira.com/rpc/soap/jirasoapservice-v2"));
  }

  @Test
  public void shouldFailToCreateSoapSessionWithIncorrectUrl() throws Exception {
    settings.removeProperty(TestTrackConstants.SERVER_URL_PROPERTY);
    settings.appendProperty(TestTrackConstants.SERVER_URL_PROPERTY, "my.server");

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("The JIRA server URL is not a valid one: my.server/rpc/soap/jirasoapservice-v2");

    jiraIssueCreator.createSoapSession(settings);
  }

  @Test
  public void shouldFailToCreateIssueIfCantConnect() throws Exception {
    // Given that
    TestTrackSoapSession soapSession = mock(TestTrackSoapSession.class);
    doThrow(RemoteException.class).when(soapSession).connect(anyString(), anyString());

    // Verify
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Impossible to connect to the JIRA server");

    jiraIssueCreator.doCreateIssue(review, soapSession, settings, null);
  }

  @Test
  public void shouldFailToCreateIssueIfCantAuthenticate() throws Exception {
    // Given that
    JiraSoapService jiraSoapService = mock(JiraSoapService.class);
    doThrow(RemoteAuthenticationException.class).when(jiraSoapService).createIssue(anyString(), any(RemoteIssue.class));

    // Verify
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Impossible to connect to the JIRA server (my.jira) because of invalid credentials for user foo");

    jiraIssueCreator.sendRequest(jiraSoapService, "", null, "my.jira", "foo");
  }

  @Test
  public void shouldFailToCreateIssueIfNotEnoughRights() throws Exception {
    // Given that
    JiraSoapService jiraSoapService = mock(JiraSoapService.class);
    doThrow(RemotePermissionException.class).when(jiraSoapService).createIssue(anyString(), any(RemoteIssue.class));

    // Verify
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Impossible to create the issue on the JIRA server (my.jira) because user foo does not have enough rights.");

    jiraIssueCreator.sendRequest(jiraSoapService, "", null, "my.jira", "foo");
  }

  @Test
  public void shouldFailToCreateIssueIfRemoteError() throws Exception {
    // Given that
    JiraSoapService jiraSoapService = mock(JiraSoapService.class);
    doThrow(RemoteException.class).when(jiraSoapService).createIssue(anyString(), any(RemoteIssue.class));

    // Verify
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Impossible to create the issue on the JIRA server (my.jira)");

    jiraIssueCreator.sendRequest(jiraSoapService, "", null, "my.jira", "foo");
  }

  @Test
  public void shouldCreateIssue() throws Exception {
    // Given that
    RemoteIssue issue = new RemoteIssue();
    JiraSoapService jiraSoapService = mock(JiraSoapService.class);
    when(jiraSoapService.createIssue(anyString(), any(RemoteIssue.class))).thenReturn(issue);

    TestTrackSoapSession soapSession = mock(TestTrackSoapSession.class);
    when(soapSession.getJiraSoapService()).thenReturn(jiraSoapService);

    // Verify
    RemoteIssue returnedIssue = jiraIssueCreator.doCreateIssue(review, soapSession, settings, null);

    verify(soapSession).connect("foo", "bar");
    verify(soapSession).getJiraSoapService();
    verify(soapSession).getAuthenticationToken();

    assertThat(returnedIssue, is(issue));
  }

  @Test
  public void shouldInitRemoteIssue() throws Exception {
    // Given that
    RemoteIssue issue = new RemoteIssue();
    issue.setProject("TEST");
    issue.setType("3");
    issue.setPriority("4");
    issue.setSummary("Sonar Review #456 - Wrong identation");
    issue.setDescription("Violation detail:\n{quote}\nThe Cyclomatic Complexity of this method is 14 which is greater than 10 authorized.\n" +
      "{quote}\n\nMessage from reviewer:\n{quote}\nHello world!\n{quote}\n\n\nCheck it on Sonar: http://my.sonar.com/project_reviews/view/456");

    // Verify
    RemoteIssue returnedIssue = jiraIssueCreator.initRemoteIssue(review, settings, "Hello world!");

    assertThat(returnedIssue, is(issue));
  }

  @Test
  public void shouldGiveDefaultPriority() throws Exception {
    assertThat(jiraIssueCreator.sonarSeverityToJiraPriority("UNKNOWN"), is("3"));
  }
}
