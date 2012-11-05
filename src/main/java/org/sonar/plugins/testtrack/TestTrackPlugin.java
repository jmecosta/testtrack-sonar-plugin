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

package org.sonar.plugins.testtrack;

import com.google.common.collect.ImmutableList;
import org.sonar.api.Properties;
import org.sonar.api.Property;
import org.sonar.api.SonarPlugin;
import org.sonar.plugins.testtrack.metrics.TestTrackMetrics;
import org.sonar.plugins.testtrack.metrics.TestTrackSensor;
import org.sonar.plugins.testtrack.metrics.TestTrackWidget;
import org.sonar.plugins.testtrack.reviews.TestTrackIssueCreator;
import org.sonar.plugins.testtrack.reviews.LinkFunction;
import org.sonar.plugins.testtrack.reviews.WorkflowBuilder;

import java.util.List;

@Properties({
  @Property(
    key = TestTrackConstants.SERVER_URL_PROPERTY,
    name = "Server URL",
    description = "Example : http://jira.codehaus.org",
    global = true,
    project = true,
    module = false
  ),
  @Property(
    key = TestTrackConstants.USERNAME_PROPERTY,
    defaultValue = "",
    name = "Username",
    global = true,
    project = true,
    module = false
  ),
  @Property(
    key = TestTrackConstants.PASSWORD_PROPERTY,
    name = "Password",
    global = true,
    project = true,
    module = false
  )
})
public final class TestTrackPlugin extends SonarPlugin {

  public List getExtensions() {
    return ImmutableList.of(
      // metrics part
      TestTrackMetrics.class, TestTrackSensor.class, TestTrackWidget.class,

      // reviews part
      TestTrackIssueCreator.class, LinkFunction.class, WorkflowBuilder.class
    );
  }
}
