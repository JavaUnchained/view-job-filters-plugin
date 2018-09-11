package hudson.views.test;

import hudson.matrix.MatrixProject;
import hudson.maven.MavenModuleSet;
import hudson.maven.reporters.MavenMailer;
import hudson.model.*;
import hudson.plugins.emailext.ExtendedEmailPublisher;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.m2extrasteps.M2ExtraStepsWrapper;
import hudson.scm.*;
import hudson.tasks.Builder;
import hudson.tasks.Mailer;
import hudson.tasks.Maven;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.DescribableList;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.*;

import static hudson.views.test.JobType.*;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class JobMocker<T extends Job> {

    public enum MavenBuildStep {
        PRE, POST
    }

    T job;

    public JobMocker(Class<T> jobClass, Class... interfaces) {
        this.job = Mockito.mock(jobClass, withSettings().extraInterfaces(interfaces).defaultAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                if (DescribableList.class.isAssignableFrom(invocationOnMock.getMethod().getReturnType())) {
                    return new DescribableList(mock(Saveable.class), new ArrayList());
                }
                return null;
            }
        }));
    }

    public static <C extends Job> JobMocker<C> jobOf(JobType<C> jobType) {
        return new JobMocker(jobType.getJobClass(), jobType.getInterfaces());
    }

    public JobMocker<T> withName(String name) {
        when(job.getName()).thenReturn(name);
        return this;
    }

    public JobMocker<T> withDesc(String name) {
        when(job.getDescription()).thenReturn(name);
        return this;
    }

    public JobMocker<T> withCVS(String root, String modules, String branch) {
        List<CvsRepository> cvsRepositories = LegacyConvertor.getInstance().convertLegacyConfigToRepositoryStructure(
                root, modules, branch,
                false, "excludedRegions",
                false, null);

        CVSSCM scm = new CVSSCM(cvsRepositories, false, false, true,  true, false, false, true);
        return withSCM(scm);
    }

    public JobMocker<T> withSVN(String... moduleLocations) {
        SubversionSCM.ModuleLocation[] locations = new SubversionSCM.ModuleLocation[moduleLocations.length];
        for (int i = 0; i < moduleLocations.length; i++) {
            locations[i] = mock(SubversionSCM.ModuleLocation.class);
            when(locations[i].getURL()).thenReturn(moduleLocations[i]);
        }

        SubversionSCM scm = mock(SubversionSCM.class);
        when(scm.getLocations()).thenReturn(locations);
        return withSCM(scm);
    }

    public JobMocker<T> withGitBranches(String... branches) {
        List<BranchSpec> branchSpecs = new ArrayList<BranchSpec>();
        for (String branch: branches) {
            BranchSpec branchSpec = mock(BranchSpec.class);
            when(branchSpec.getName()).thenReturn(branch);
            branchSpecs.add(branchSpec);
        }

        GitSCM scm = mock(GitSCM.class);
        when(scm.getBranches()).thenReturn(branchSpecs);
        return withSCM(scm);
    }

    public JobMocker<T> withGitRepos(String... repos) {
        List<RemoteConfig> remotes = new ArrayList<RemoteConfig>();
        for (String repo: repos) {
            URIish uri = mock(URIish.class);
            when(uri.toPrivateString()).thenReturn(repo);

            RemoteConfig remote = mock(RemoteConfig.class);
            when(remote.getURIs()).thenReturn(Arrays.asList(uri));
            remotes.add(remote);
        }

        GitSCM scm = mock(GitSCM.class);
        when(scm.getRepositories()).thenReturn(remotes);
        return withSCM(scm);
    }

    public JobMocker<T> withLegacyGitRepos(String... repos) {
        List<org.spearce.jgit.transport.RemoteConfig> remotes = new ArrayList<org.spearce.jgit.transport.RemoteConfig>();
        for (String repo: repos) {
            org.spearce.jgit.transport.URIish uri = mock(org.spearce.jgit.transport.URIish.class);
            when(uri.toPrivateString()).thenReturn(repo);

            org.spearce.jgit.transport.RemoteConfig remote = mock(org.spearce.jgit.transport.RemoteConfig.class);
            when(remote.getURIs()).thenReturn(Arrays.asList(uri));
            remotes.add(remote);
        }

        GitSCM scm = mock(GitSCM.class);
        when(scm.getRepositories()).thenReturn((List)remotes);
        return withSCM(scm);
    }

    public JobMocker withSCM(SCM scm) {
        if (job instanceof AbstractProject) {
            when(((AbstractProject)job).getScm()).thenReturn(scm);
        } else if (instanceOf(job, SCMED_ITEM)) {
            when(((SCMedItem)job).getScm()).thenReturn(scm);
        }
        return this;
    }

    public JobMocker<T> withEmail(String email) {
        if (job instanceof AbstractProject) {
            Mailer mailer = new Mailer(email, false, false);

            DescribableList publishers = new DescribableList(mock(Saveable.class), asList(mailer));
            when(((AbstractProject)job).getPublishersList()).thenReturn(publishers);
        }
        if (instanceOf(job, MAVEN_MODULE_SET)) {
            MavenMailer mavenMailer = new MavenMailer();
            mavenMailer.recipients = email;

            DescribableList reporters = new DescribableList(mock(Saveable.class), asList(mavenMailer));
            when(((MavenModuleSet)job).getReporters()).thenReturn(reporters);
        }
        return this;
    }

    public JobMocker<T> withExtEmail(String email) {
        ExtendedEmailPublisher emailPublisher = new ExtendedEmailPublisher();
        emailPublisher.recipientList = email;

        DescribableList publishers = new DescribableList(mock(Saveable.class), asList(emailPublisher));

        if (job instanceof AbstractProject) {
            when(((AbstractProject)job).getPublishersList()).thenReturn(publishers);
        }
        if (instanceOf(job, MAVEN_MODULE_SET)) {
            DescribableList reporters = new DescribableList(mock(Saveable.class), new ArrayList());
            when(((MavenModuleSet)job).getReporters()).thenReturn(reporters);
        }
        return this;
    }

    public JobMocker<T> withTrigger(String spec) {
        Trigger trigger = mock(Trigger.class);
        when(trigger.getSpec()).thenReturn(spec);

        Map<TriggerDescriptor, Trigger<?>> triggers = new HashMap<TriggerDescriptor, Trigger<?>>();
        triggers.put(mock(TriggerDescriptor.class), trigger);

        if (job instanceof AbstractProject) {
            when(((AbstractProject)job).getTriggers()).thenReturn(triggers);
        }
        return this;
    }

    public T asJob() {
        return job;
    }

    public TopLevelItem asItem() {
        return (TopLevelItem)job;
    }
}