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

package org.sonar.plugins.testtrack.metrics;

import com.atlassian.jira.rpc.soap.client.JiraSoapService;
import com.atlassian.jira.rpc.soap.client.RemoteFilter;
import com.atlassian.jira.rpc.soap.client.RemoteIssue;
import com.atlassian.jira.rpc.soap.client.RemotePriority;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.config.Settings;
import org.sonar.api.resources.Project;
import org.sonar.api.test.IsMeasure;
import org.sonar.plugins.testtrack.TestTrackConstants;

import java.rmi.RemoteException;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class TestTrackSensorTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private TestTrackSensor sensor;
  private Settings settings;

  @Before
  public void setUp() {
    settings = new Settings();
    settings.setProperty(TestTrackConstants.SERVER_URL_PROPERTY, "http://my.jira.server");
    settings.setProperty(TestTrackConstants.USERNAME_PROPERTY, "admin");
    settings.setProperty(TestTrackConstants.PASSWORD_PROPERTY, "adminPwd");
    settings.setProperty(TestTrackConstants.FILTER_PROPERTY, "myFilter");
    sensor = new TestTrackSensor(settings);
  }

  @Test
  public void testToString() throws Exception {
    assertThat(sensor.toString(), is("JIRA issues sensor"));
  }

  @Test
  public void testPresenceOfProperties() throws Exception {
    assertThat(sensor.missingMandatoryParameters(), is(false));

    settings.removeProperty(TestTrackConstants.PASSWORD_PROPERTY);
    sensor = new TestTrackSensor(settings);
    assertThat(sensor.missingMandatoryParameters(), is(true));

    settings.removeProperty(TestTrackConstants.USERNAME_PROPERTY);
    sensor = new TestTrackSensor(settings);
    assertThat(sensor.missingMandatoryParameters(), is(true));

    settings.removeProperty(TestTrackConstants.FILTER_PROPERTY);
    sensor = new TestTrackSensor(settings);
    assertThat(sensor.missingMandatoryParameters(), is(true));

    settings.removeProperty(TestTrackConstants.SERVER_URL_PROPERTY);
    sensor = new TestTrackSensor(settings);
    assertThat(sensor.missingMandatoryParameters(), is(true));
  }

  @Test
  public void shouldExecuteOnRootProjectWithAllParams() throws Exception {
    Project project = mock(Project.class);
    when(project.isRoot()).thenReturn(true).thenReturn(false);

    assertThat(sensor.shouldExecuteOnProject(project), is(true));
  }

  @Test
  public void shouldNotExecuteOnNonRootProject() throws Exception {
    assertThat(sensor.shouldExecuteOnProject(mock(Project.class)), is(false));
  }

  @Test
  public void shouldNotExecuteOnRootProjectifOneParamMissing() throws Exception {
    Project project = mock(Project.class);
    when(project.isRoot()).thenReturn(true).thenReturn(false);

    settings.removeProperty(TestTrackConstants.SERVER_URL_PROPERTY);
    sensor = new TestTrackSensor(settings);

    assertThat(sensor.shouldExecuteOnProject(project), is(false));
  }

  @Test
  public void testSaveMeasures() {
    SensorContext context = mock(SensorContext.class);
    String url = "http://localhost/jira";
    String priorityDistribution = "Critical=1";

    sensor.saveMeasures(context, url, 1, priorityDistribution);

    verify(context).saveMeasure(argThat(new IsMeasure(TestTrackMetrics.ISSUES, 1.0, priorityDistribution)));
    verifyNoMoreInteractions(context);
  }

  @Test
  public void shouldCollectPriorities() throws Exception {
    JiraSoapService jiraSoapService = mock(JiraSoapService.class);
    RemotePriority priority1 = new RemotePriority();
    priority1.setId("1");
    priority1.setName("Minor");
    when(jiraSoapService.getPriorities("token")).thenReturn(new RemotePriority[] {priority1});

    Map<String, String> foundPriorities = sensor.collectPriorities(jiraSoapService, "token");
    assertThat(foundPriorities.size(), is(1));
    assertThat(foundPriorities.get("1"), is("Minor"));
  }

  @Test
  public void shouldCollectIssuesByPriority() throws Exception {
    RemoteFilter filter = new RemoteFilter();
    filter.setId("1");
    JiraSoapService jiraSoapService = mock(JiraSoapService.class);
    RemoteIssue issue1 = new RemoteIssue();
    issue1.setPriority("minor");
    RemoteIssue issue2 = new RemoteIssue();
    issue2.setPriority("critical");
    RemoteIssue issue3 = new RemoteIssue();
    issue3.setPriority("critical");
    when(jiraSoapService.getIssuesFromFilter("token", "1")).thenReturn(new RemoteIssue[] {issue1, issue2, issue3});

    Map<String, Integer> foundIssues = sensor.collectIssuesByPriority(jiraSoapService, "token", filter);
    assertThat(foundIssues.size(), is(2));
    assertThat(foundIssues.get("critical"), is(2));
    assertThat(foundIssues.get("minor"), is(1));
  }

  @Test
  public void shouldFindFilters() throws Exception {
    JiraSoapService jiraSoapService = mock(JiraSoapService.class);
    RemoteFilter filter1 = new RemoteFilter();
    filter1.setName("fooFilter");
    RemoteFilter myFilter = new RemoteFilter();
    myFilter.setName("myFilter");
    when(jiraSoapService.getFavouriteFilters("token")).thenReturn(new RemoteFilter[] {filter1, myFilter});

    RemoteFilter foundFilter = sensor.findJiraFilter(jiraSoapService, "token");
    assertThat(foundFilter, is(myFilter));
  }

  @Test
  public void shouldFindFiltersWithPreviousJiraVersions() throws Exception {
    JiraSoapService jiraSoapService = mock(JiraSoapService.class);
    RemoteFilter myFilter = new RemoteFilter();
    myFilter.setName("myFilter");
    when(jiraSoapService.getSavedFilters("token")).thenReturn(new RemoteFilter[] {myFilter});
    when(jiraSoapService.getFavouriteFilters("token")).thenThrow(RemoteException.class);

    RemoteFilter foundFilter = sensor.findJiraFilter(jiraSoapService, "token");
    assertThat(foundFilter, is(myFilter));
  }

  @Test
  public void faillIfNoFilterFound() throws Exception {
    JiraSoapService jiraSoapService = mock(JiraSoapService.class);
    when(jiraSoapService.getFavouriteFilters("token")).thenReturn(new RemoteFilter[0]);

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Unable to find filter 'myFilter' in JIRA");

    sensor.findJiraFilter(jiraSoapService, "token");
  }

}
