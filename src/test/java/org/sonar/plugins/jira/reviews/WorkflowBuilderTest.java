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

import org.junit.Test;
import org.sonar.api.workflow.Workflow;
import org.sonar.api.workflow.condition.Condition;
import org.sonar.api.workflow.screen.Screen;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class WorkflowBuilderTest {

  @Test
  public void checkStart() throws Exception {
    Workflow workflow = mock(Workflow.class);
    LinkFunction function = mock(LinkFunction.class);

    WorkflowBuilder builder = new WorkflowBuilder(workflow, function);
    builder.start();

    verify(workflow, times(1)).addCommand("link-to-jira");
    verify(workflow, times(1)).setScreen(anyString(), any(Screen.class));
    verify(workflow, times(1)).addFunction("link-to-jira", function);
    verify(workflow, times(7)).addCondition(anyString(), any(Condition.class));
  }

}
