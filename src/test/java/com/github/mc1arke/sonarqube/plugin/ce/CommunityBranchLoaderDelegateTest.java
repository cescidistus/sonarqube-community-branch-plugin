/*
 * Copyright (C) 2020 Michael Clarke
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */
package com.github.mc1arke.sonarqube.plugin.ce;

import org.hamcrest.core.IsEqual;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.sonar.ce.task.projectanalysis.analysis.Branch;
import org.sonar.ce.task.projectanalysis.analysis.MutableAnalysisMetadataHolder;
import org.sonar.db.DbClient;
import org.sonar.db.component.BranchDao;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.scanner.protocol.output.ScannerReport;
import org.sonar.server.project.Project;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Michael Clarke
 */
public class CommunityBranchLoaderDelegateTest {

    private final ExpectedException expectedException = ExpectedException.none();

    @Rule
    public ExpectedException expectedException() {
        return expectedException;
    }

    @Test
    public void testNoBranchDetailsNoExistingBranchThrowsException() {

        BranchDao branchDao = mock(BranchDao.class);
        when(branchDao.selectByBranchKey(any(), any(), any())).thenReturn(Optional.empty());

        ScannerReport.Metadata metadata = ScannerReport.Metadata.getDefaultInstance();
        DbClient dbClient = mock(DbClient.class);
        when(dbClient.branchDao()).thenReturn(branchDao);
        MutableAnalysisMetadataHolder metadataHolder = mock(MutableAnalysisMetadataHolder.class);
        when(metadataHolder.getProject()).thenReturn(new Project("uuid", "key", "name", "description", new ArrayList<>()));

        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage(IsEqual.equalTo("Could not find main branch"));

        new CommunityBranchLoaderDelegate(dbClient, metadataHolder).load(metadata);
    }

    @Test
    public void testNoBranchDetailsExistingBranchMatch() {
        BranchDto branchDto = mock(BranchDto.class);
        when(branchDto.getBranchType()).thenReturn(BranchType.BRANCH);
        when(branchDto.getKey()).thenReturn("branchKey");
        when(branchDto.getMergeBranchUuid()).thenReturn("mergeBranchUuid");
        when(branchDto.getProjectUuid()).thenReturn("projectUuid");
        when(branchDto.getUuid()).thenReturn("branchUuid");

        BranchDao branchDao = mock(BranchDao.class);
        when(branchDao.selectByUuid(any(), any())).thenReturn(Optional.of(branchDto));

        ScannerReport.Metadata metadata = ScannerReport.Metadata.getDefaultInstance();
        DbClient dbClient = mock(DbClient.class);
        when(dbClient.branchDao()).thenReturn(branchDao);
        MutableAnalysisMetadataHolder metadataHolder = mock(MutableAnalysisMetadataHolder.class);
        when(metadataHolder.getProject()).thenReturn(new Project("uuid", "key", "name", "description", new ArrayList<>()));

        new CommunityBranchLoaderDelegate(dbClient, metadataHolder).load(metadata);

        ArgumentCaptor<CommunityBranch> branchArgumentCaptor = ArgumentCaptor.forClass(CommunityBranch.class);

        verify(metadataHolder).setBranch(branchArgumentCaptor.capture());
        assertEquals(BranchType.BRANCH, branchArgumentCaptor.getValue().getType());
        assertNull(branchArgumentCaptor.getValue().getTargetBranchName());
        assertEquals("branchKey", branchArgumentCaptor.getValue().getName());
        assertFalse(branchArgumentCaptor.getValue().isMain());
        assertFalse(branchArgumentCaptor.getValue().supportsCrossProjectCpd());
        assertNull(branchArgumentCaptor.getValue().getTargetBranchName());
    }

    @Test
    public void testBranchNameNoMatchingBranch() {

        BranchDao branchDao = mock(BranchDao.class);
        when(branchDao.selectByBranchKey(any(), any(), any())).thenReturn(Optional.empty());

        ScannerReport.Metadata metadata =
                ScannerReport.Metadata.getDefaultInstance().toBuilder().setBranchName("branch").build();

        DbClient dbClient = mock(DbClient.class);
        when(dbClient.branchDao()).thenReturn(branchDao);

        MutableAnalysisMetadataHolder metadataHolder = mock(MutableAnalysisMetadataHolder.class);
        when(metadataHolder.getProject()).thenReturn(new Project("uuid", "key", "name", "description", new ArrayList<>()));

        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage(IsEqual.equalTo("Invalid branch type 'UNSET'"));

        new CommunityBranchLoaderDelegate(dbClient, metadataHolder).load(metadata);
    }

    @Test
    public void testBranchNameMatchingBranch() {
        BranchDto branchDto = mock(BranchDto.class);
        when(branchDto.getBranchType()).thenReturn(BranchType.BRANCH);
        when(branchDto.getKey()).thenReturn("branchKey");
        when(branchDto.getMergeBranchUuid()).thenReturn("mergeBranchUuid");
        when(branchDto.getProjectUuid()).thenReturn("projectUuid");
        when(branchDto.getUuid()).thenReturn("branchUuid");

        BranchDao branchDao = mock(BranchDao.class);
        when(branchDao.selectByBranchKey(any(), eq("projectUuid"), eq("branch"))).thenReturn(Optional.of(branchDto));

        ScannerReport.Metadata metadata =
                ScannerReport.Metadata.getDefaultInstance().toBuilder().setBranchName("branch")
                        .setBranchType(ScannerReport.Metadata.BranchType.BRANCH).build();

        DbClient dbClient = mock(DbClient.class);
        when(dbClient.branchDao()).thenReturn(branchDao);

        MutableAnalysisMetadataHolder metadataHolder = mock(MutableAnalysisMetadataHolder.class);
        when(metadataHolder.getProject()).thenReturn(new Project("projectUuid", "key", "name", "description", new ArrayList<>()));

        new CommunityBranchLoaderDelegate(dbClient, metadataHolder).load(metadata);

        ArgumentCaptor<CommunityBranch> branchArgumentCaptor = ArgumentCaptor.forClass(CommunityBranch.class);

        verify(metadataHolder).setBranch(branchArgumentCaptor.capture());
        assertEquals(BranchType.BRANCH, branchArgumentCaptor.getValue().getType());
        assertEquals("projectUuid", branchArgumentCaptor.getValue().getReferenceBranchUuid());
        assertEquals("branch", branchArgumentCaptor.getValue().getName());
        assertFalse(branchArgumentCaptor.getValue().isMain());
        assertFalse(branchArgumentCaptor.getValue().supportsCrossProjectCpd());
        assertNull(branchArgumentCaptor.getValue().getTargetBranchName());
    }

    @Test
    public void testBranchNamePullRequest() {
        BranchDto branchDto = mock(BranchDto.class);
        when(branchDto.getBranchType()).thenReturn(BranchType.PULL_REQUEST);
        when(branchDto.getKey()).thenReturn("branchKey");
        when(branchDto.getUuid()).thenReturn("mergeBranchUuid");
        when(branchDto.getProjectUuid()).thenReturn("projectUuid");

        BranchDao branchDao = mock(BranchDao.class);
        when(branchDao.selectByBranchKey(any(), eq("projectUuid"), eq("branch"))).thenReturn(Optional.of(branchDto));

        ScannerReport.Metadata metadata =
                ScannerReport.Metadata.getDefaultInstance().toBuilder().setBranchName("sourceBranch")
                        .setReferenceBranchName("branch").setBranchType(ScannerReport.Metadata.BranchType.PULL_REQUEST)
                        .setTargetBranchName("")
                        .build();

        DbClient dbClient = mock(DbClient.class);
        when(dbClient.branchDao()).thenReturn(branchDao);

        MutableAnalysisMetadataHolder metadataHolder = mock(MutableAnalysisMetadataHolder.class);
        when(metadataHolder.getProject()).thenReturn(new Project("projectUuid", "key", "name", "description", new ArrayList<>()));

        new CommunityBranchLoaderDelegate(dbClient, metadataHolder).load(metadata);

        ArgumentCaptor<CommunityBranch> branchArgumentCaptor = ArgumentCaptor.forClass(CommunityBranch.class);

        verify(metadataHolder).setBranch(branchArgumentCaptor.capture());
        assertEquals(BranchType.PULL_REQUEST, branchArgumentCaptor.getValue().getType());
        assertEquals("mergeBranchUuid", branchArgumentCaptor.getValue().getReferenceBranchUuid());
        assertEquals("sourceBranch", branchArgumentCaptor.getValue().getName());
        assertFalse(branchArgumentCaptor.getValue().isMain());
        assertFalse(branchArgumentCaptor.getValue().supportsCrossProjectCpd());
        assertEquals("branch", branchArgumentCaptor.getValue().getTargetBranchName());
    }

    @Test
    public void testBranchNamePullRequestNoSuchTarget() {
        BranchDao branchDao = mock(BranchDao.class);
        when(branchDao.selectByBranchKey(any(), eq("projectUuid"), eq("branch"))).thenReturn(Optional.empty());

        ScannerReport.Metadata metadata =
                ScannerReport.Metadata.getDefaultInstance().toBuilder().setBranchName("sourceBranch")
                        .setReferenceBranchName("branch").setBranchType(ScannerReport.Metadata.BranchType.PULL_REQUEST)
                        .build();

        DbClient dbClient = mock(DbClient.class);
        when(dbClient.branchDao()).thenReturn(branchDao);

        MutableAnalysisMetadataHolder metadataHolder = mock(MutableAnalysisMetadataHolder.class);
        when(metadataHolder.getProject()).thenReturn(new Project("projectUuid", "key", "name", "description", new ArrayList<>()));

        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage(IsEqual.equalTo("Could not find target branch 'branch' in project"));


        new CommunityBranchLoaderDelegate(dbClient, metadataHolder).load(metadata);
    }


    @Test
    public void testBranchNameMatchingBranchWithTargetBranch() {
        BranchDto sourceBranchDto = mock(BranchDto.class);
        when(sourceBranchDto.getBranchType()).thenReturn(BranchType.BRANCH);
        when(sourceBranchDto.getKey()).thenReturn("branchKey");
        when(sourceBranchDto.getMergeBranchUuid()).thenReturn("mergeBranchUuid");
        when(sourceBranchDto.getProjectUuid()).thenReturn("projectUuid");
        when(sourceBranchDto.getUuid()).thenReturn("branchUuid");

        BranchDto targetBranchDto = mock(BranchDto.class);
        when(targetBranchDto.getBranchType()).thenReturn(BranchType.PULL_REQUEST);
        when(targetBranchDto.getKey()).thenReturn("targetBranchKey");
        when(targetBranchDto.getMergeBranchUuid()).thenReturn("targetMergeBranchUuid");
        when(targetBranchDto.getProjectUuid()).thenReturn("projectUuid");
        when(targetBranchDto.getUuid()).thenReturn("targetBranchUuid");

        BranchDao branchDao = mock(BranchDao.class);
        when(branchDao.selectByBranchKey(any(), eq("projectUuid"), eq("branch")))
                .thenReturn(Optional.of(sourceBranchDto));
        when(branchDao.selectByBranchKey(any(), eq("projectUuid"), eq("mergeBranchName")))
                .thenReturn(Optional.of(targetBranchDto));

        ScannerReport.Metadata metadata =
                ScannerReport.Metadata.getDefaultInstance().toBuilder().setBranchName("branch")
                        .setBranchType(ScannerReport.Metadata.BranchType.BRANCH)
                        .setReferenceBranchName("mergeBranchName")
                        .setTargetBranchName("targetBranchThatDoesNotMatchMergeBranch")
                        .build();

        DbClient dbClient = mock(DbClient.class);
        when(dbClient.branchDao()).thenReturn(branchDao);

        MutableAnalysisMetadataHolder metadataHolder = mock(MutableAnalysisMetadataHolder.class);
        when(metadataHolder.getProject()).thenReturn(new Project("projectUuid", "key", "name", "description", new ArrayList<>()));

        new CommunityBranchLoaderDelegate(dbClient, metadataHolder).load(metadata);

        ArgumentCaptor<Branch> branchArgumentCaptor = ArgumentCaptor.forClass(Branch.class);

        verify(metadataHolder).setBranch(branchArgumentCaptor.capture());
        assertEquals(BranchType.BRANCH, branchArgumentCaptor.getValue().getType());
        assertEquals("targetBranchUuid", branchArgumentCaptor.getValue().getReferenceBranchUuid());
        assertEquals("branch", branchArgumentCaptor.getValue().getName());
        assertFalse(branchArgumentCaptor.getValue().isMain());
        assertFalse(branchArgumentCaptor.getValue().supportsCrossProjectCpd());
        assertEquals("targetBranchThatDoesNotMatchMergeBranch", branchArgumentCaptor.getValue().getTargetBranchName());
    }

    @Test
    public void testBranchNameMatchingBranchWithTargetBranchMissingTargetBranch() {
        BranchDto sourceBranchDto = mock(BranchDto.class);
        when(sourceBranchDto.getBranchType()).thenReturn(BranchType.BRANCH);
        when(sourceBranchDto.getKey()).thenReturn("branchKey");
        when(sourceBranchDto.getMergeBranchUuid()).thenReturn("mergeBranchUuid");
        when(sourceBranchDto.getProjectUuid()).thenReturn("projectUuid");
        when(sourceBranchDto.getUuid()).thenReturn("branchUuid");

        BranchDao branchDao = mock(BranchDao.class);
        when(branchDao.selectByBranchKey(any(), eq("projectUuid"), eq("branch")))
                .thenReturn(Optional.of(sourceBranchDto));
        when(branchDao.selectByBranchKey(any(), eq("projectUuid"), eq("mergeBranchName"))).thenReturn(Optional.empty());

        ScannerReport.Metadata metadata =
                ScannerReport.Metadata.getDefaultInstance().toBuilder().setBranchName("branch")
                        .setBranchType(ScannerReport.Metadata.BranchType.BRANCH)
                        .setReferenceBranchName("mergeBranchName")
                        .build();

        DbClient dbClient = mock(DbClient.class);
        when(dbClient.branchDao()).thenReturn(branchDao);

        MutableAnalysisMetadataHolder metadataHolder = mock(MutableAnalysisMetadataHolder.class);
        when(metadataHolder.getProject()).thenReturn(new Project("projectUuid", "key", "name", "description", new ArrayList<>()));


        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage(IsEqual.equalTo("Could not find target branch 'mergeBranchName' in project"));

        new CommunityBranchLoaderDelegate(dbClient, metadataHolder).load(metadata);
    }

}
