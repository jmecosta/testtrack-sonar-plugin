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
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.Properties;
import org.sonar.api.Property;
import org.sonar.api.ServerExtension;
import org.sonar.api.config.Settings;
import org.sonar.api.workflow.Review;
import org.sonar.plugins.testtrack.TestTrackConstants;
import org.sonar.plugins.testtrack.soap.TestTrackSoapSession;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Map;

/**
 * SOAP client class that is used for creating issues on a JIRA server
 */
@Properties({
  @Property(
    key = TestTrackConstants.SOAP_BASE_URL_PROPERTY,
    defaultValue = TestTrackConstants.SOAP_BASE_URL_DEF_VALUE,
    name = "SOAP base URL",
    description = "Base URL for the SOAP API of the JIRA server",
    global = true,
    project = true
  ),
  @Property(
    key = TestTrackConstants.JIRA_PROJECT_KEY_PROPERTY,
    defaultValue = "",
    name = "JIRA project key",
    description = "Key of the JIRA project on which the issues should be created.",
    global = false,
    project = true
  )
})
public class TestTrackIssueCreator implements ServerExtension {

  private static final String QUOTE = "\n{quote}\n";
  private static final Logger LOG = LoggerFactory.getLogger(TestTrackIssueCreator.class);
  private static final String TASK_ISSUE_TYPE = "3";
  private static final Map<String, String> SONAR_SEVERITY_TO_JIRA_PRIORITY = new ImmutableMap.Builder<String, String>()
    .put("BLOCKER", "1")
    .put("CRITICAL", "2")
    .put("MAJOR", "3")
    .put("MINOR", "4")
    .put("INFO", "5")
    .build();

  public TestTrackIssueCreator() {
  }

  @SuppressWarnings("rawtypes")
  public RemoteIssue createIssue(Review review, Settings settings, String commentText) throws RemoteException {
    TestTrackSoapSession soapSession = createSoapSession(settings);

    return doCreateIssue(review, soapSession, settings, commentText);
  }

  protected TestTrackSoapSession createSoapSession(Settings settings) {
    String jiraUrl = settings.getString(TestTrackConstants.SERVER_URL_PROPERTY);
    String baseUrl = settings.getString(TestTrackConstants.SOAP_BASE_URL_PROPERTY);
    String completeUrl = jiraUrl + baseUrl;

    // get handle to the JIRA SOAP Service from a client point of view
    TestTrackSoapSession soapSession = null;
    try {
      soapSession = new TestTrackSoapSession(new URL(completeUrl));
    } catch (MalformedURLException e) {
      LOG.error("The JIRA server URL is not a valid one: " + completeUrl, e);
      throw new IllegalStateException("The JIRA server URL is not a valid one: " + completeUrl, e);
    }
    return soapSession;
  }

  protected RemoteIssue doCreateIssue(Review review, TestTrackSoapSession soapSession, Settings settings, String commentText) {
    // Connect to JIRA
    String jiraUrl = settings.getString(TestTrackConstants.SERVER_URL_PROPERTY);
    String userName = settings.getString(TestTrackConstants.USERNAME_PROPERTY);
    String password = settings.getString(TestTrackConstants.PASSWORD_PROPERTY);
    try {
      soapSession.connect(userName, password);
    } catch (RemoteException e) {
      throw new IllegalStateException("Impossible to connect to the JIRA server (" + jiraUrl + ").", e);
    }

    // The JIRA SOAP Service and authentication token are used to make authentication calls
    JiraSoapService jiraSoapService = soapSession.getJiraSoapService();
    String authToken = soapSession.getAuthenticationToken();

    // And create the issue
    RemoteIssue issue = initRemoteIssue(review, settings, commentText);
    RemoteIssue returnedIssue = sendRequest(jiraSoapService, authToken, issue, jiraUrl, userName);

    String issueKey = returnedIssue.getKey();
    LOG.debug("Successfully created issue {}", issueKey);

    return returnedIssue;
  }

  protected RemoteIssue sendRequest(JiraSoapService jiraSoapService, String authToken, RemoteIssue issue, String jiraUrl, String userName) {
    try {
      return jiraSoapService.createIssue(authToken, issue);
    } catch (RemoteAuthenticationException e) {
      throw new IllegalStateException("Impossible to connect to the JIRA server (" + jiraUrl + ") because of invalid credentials for user " + userName, e);
    } catch (RemotePermissionException e) {
      throw new IllegalStateException("Impossible to create the issue on the JIRA server (" + jiraUrl + ") because user " + userName + " does not have enough rights.", e);
    } catch (RemoteException e) {
      throw new IllegalStateException("Impossible to create the issue on the JIRA server (" + jiraUrl + ")", e);
    }
  }

  protected RemoteIssue initRemoteIssue(Review review, Settings settings, String commentText) {
    RemoteIssue issue = new RemoteIssue();
    issue.setProject(settings.getString(TestTrackConstants.JIRA_PROJECT_KEY_PROPERTY));
    issue.setType(TASK_ISSUE_TYPE);
    issue.setPriority(sonarSeverityToJiraPriority(review.getSeverity()));
    issue.setSummary(generateIssueSummary(review));
    issue.setDescription(generateIssueDescription(review, settings, commentText));
    return issue;
  }

  protected String generateIssueSummary(Review review) {
    StringBuilder summary = new StringBuilder("Sonar Review #");
    summary.append(review.getReviewId());
    summary.append(" - ");
    summary.append(review.getRuleName());
    return summary.toString();
  }

  protected String generateIssueDescription(Review review, Settings settings, String commentText) {
    StringBuilder description = new StringBuilder("Violation detail:");
    description.append(QUOTE);
    description.append(review.getMessage());
    description.append(QUOTE);
    if (StringUtils.isNotBlank(commentText)) {
      description.append("\nMessage from reviewer:");
      description.append(QUOTE);
      description.append(commentText);
      description.append(QUOTE);
    }
    description.append("\n\nCheck it on Sonar: ");
    description.append(settings.getString("sonar.core.serverBaseURL"));
    description.append("/project_reviews/view/");
    description.append(review.getReviewId());
    return description.toString();
  }

  protected String sonarSeverityToJiraPriority(String reviewSeverity) {
    String priority = SONAR_SEVERITY_TO_JIRA_PRIORITY.get(reviewSeverity);
    if (priority == null) {
      // default to MAJOR
      priority = "3";
    }
    return priority;
  }

}
