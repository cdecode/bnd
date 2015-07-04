package bndtools.model.repo;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;

import org.bndtools.api.ILogger;
import org.bndtools.api.Logger;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.service.repository.Repository;

import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import aQute.bnd.build.WorkspaceRepository;
import aQute.bnd.osgi.Builder;
import aQute.bnd.service.IndexProvider;
import aQute.bnd.service.RepositoryPlugin;
import aQute.bnd.service.ResolutionPhase;
import aQute.bnd.version.Version;
import bndtools.central.Central;
import bndtools.central.WorkspaceR5Repository;

public class RepositoryTreeContentProvider implements ITreeContentProvider {

    private static final String CACHE_REPOSITORY = "cache";
    private static final ILogger logger = Logger.getLogger(RepositoryTreeContentProvider.class);

    private final EnumSet<ResolutionPhase> phases;

    private String rawFilter = null;
    private String wildcardFilter = null;
    private boolean showRepos = true;

    private Requirement requirementFilter = null;

    public RepositoryTreeContentProvider() {
        this.phases = EnumSet.allOf(ResolutionPhase.class);
    }

    public RepositoryTreeContentProvider(ResolutionPhase mode) {
        this.phases = EnumSet.of(mode);
    }

    public RepositoryTreeContentProvider(EnumSet<ResolutionPhase> modes) {
        this.phases = modes;
    }

    public String getFilter() {
        return rawFilter;
    }

    public void setFilter(String filter) {
        this.rawFilter = filter;
        if (filter == null || filter.length() == 0 || filter.trim().equals("*"))
            wildcardFilter = null;
        else
            wildcardFilter = "*" + filter.trim() + "*";
    }

    public void setRequirementFilter(Requirement requirement) {
        this.requirementFilter = requirement;
    }

    public void setShowRepos(boolean showRepos) {
        this.showRepos = showRepos;
    }

    public boolean isShowRepos() {
        return showRepos;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object[] getElements(Object inputElement) {
        Collection<Object> result;
        if (inputElement instanceof Workspace) {
            result = new ArrayList<Object>();
            Workspace workspace = (Workspace) inputElement;
            addRepositoryPlugins(result, workspace);
        } else if (inputElement instanceof Collection) {
            result = new ArrayList<Object>();
            addCollection(result, (Collection<Object>) inputElement);
        } else if (inputElement instanceof Object[]) {
            result = new ArrayList<Object>();
            addCollection(result, Arrays.asList(inputElement));
        } else {
            result = Collections.emptyList();
        }

        return result.toArray(new Object[result.size()]);
    }

    @Override
    public void dispose() {}

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

    @Override
    public Object[] getChildren(Object parentElement) {
        Object[] result = null;

        if (parentElement instanceof RepositoryPlugin) {
            RepositoryPlugin repo = (RepositoryPlugin) parentElement;
            result = getRepositoryBundles(repo);
        } else if (parentElement instanceof RepositoryBundle) {
            RepositoryBundle bundle = (RepositoryBundle) parentElement;
            result = getRepositoryBundleVersions(bundle);
        } else if (parentElement instanceof Project) {
            Project project = (Project) parentElement;
            result = getProjectBundles(project);
        }

        return result;
    }

    @Override
    public Object getParent(Object element) {
        if (element instanceof RepositoryBundle) {
            return ((RepositoryBundle) element).getRepo();
        }
        if (element instanceof RepositoryBundleVersion) {
            return ((RepositoryBundleVersion) element).getBundle();
        }
        return null;
    }

    @Override
    public boolean hasChildren(Object element) {
        return element instanceof RepositoryPlugin || element instanceof RepositoryBundle || element instanceof Project;
    }

    private void addRepositoryPlugins(Collection<Object> result, Workspace workspace) {
        workspace.getErrors().clear();
        List<RepositoryPlugin> repoPlugins = workspace.getPlugins(RepositoryPlugin.class);
        for (String error : workspace.getErrors()) {
            logger.logError(error, null);
        }
        for (RepositoryPlugin repoPlugin : repoPlugins) {
            if (CACHE_REPOSITORY.equals(repoPlugin.getName()))
                continue;
            if (repoPlugin instanceof IndexProvider) {
                IndexProvider indexProvider = (IndexProvider) repoPlugin;
                if (!supportsPhase(indexProvider))
                    continue;
            }
            if (showRepos)
                result.add(repoPlugin);
            else
                result.addAll(Arrays.asList(getRepositoryBundles(repoPlugin)));
        }
    }

    private void addCollection(Collection<Object> result, Collection<Object> inputs) {
        for (Object input : inputs) {
            if (input instanceof RepositoryPlugin) {
                RepositoryPlugin repo = (RepositoryPlugin) input;
                if (repo instanceof IndexProvider) {
                    if (!supportsPhase((IndexProvider) repo))
                        continue;
                }

                if (showRepos) {
                    result.add(repo);
                } else {
                    Object[] bundles = getRepositoryBundles(repo);
                    if (bundles != null && bundles.length > 0)
                        result.addAll(Arrays.asList(bundles));
                }
            }
        }
    }

    private boolean supportsPhase(IndexProvider provider) {
        Set<ResolutionPhase> supportedPhases = provider.getSupportedPhases();
        for (ResolutionPhase phase : phases) {
            if (supportedPhases.contains(phase))
                return true;
        }
        return false;
    }

    Object[] getProjectBundles(Project project) {
        ProjectBundle[] result = null;
        try {
            Collection< ? extends Builder> builders = project.getSubBuilders();
            result = new ProjectBundle[builders.size()];

            int i = 0;
            for (Builder builder : builders) {
                ProjectBundle bundle = new ProjectBundle(project, builder.getBsn());
                result[i++] = bundle;
            }
        } catch (Exception e) {
            logger.logError(MessageFormat.format("Error querying sub-bundles for project {0}.", project.getName()), e);
        }
        return result;
    }

    Object[] getRepositoryBundleVersions(RepositoryBundle bundle) {
        RepositoryBundleVersion[] result = null;

        SortedSet<Version> versions = null;
        try {
            versions = bundle.getRepo().versions(bundle.getBsn());
        } catch (Exception e) {
            logger.logError(MessageFormat.format("Error querying versions for bundle {0} in repository {1}.", bundle.getBsn(), bundle.getRepo().getName()), e);
        }
        if (versions != null) {
            result = new RepositoryBundleVersion[versions.size()];
            int i = 0;
            for (Version version : versions) {
                result[i++] = new RepositoryBundleVersion(bundle, version);
            }
        }
        return result;
    }

    Object[] getRepositoryBundles(RepositoryPlugin repoPlugin) {
        Object[] result = null;

        if (requirementFilter != null) {
            if (repoPlugin instanceof Repository) {
                result = searchR5Repository((Repository) repoPlugin);
            } else if (repoPlugin instanceof WorkspaceRepository) {
                try {
                    WorkspaceR5Repository workspaceRepo = Central.getWorkspaceR5Repository();
                    result = searchR5Repository(workspaceRepo);
                } catch (Exception e) {
                    logger.logError("Error querying workspace repository", e);
                }
            }
            return result;
        }

        List<String> bsns = null;
        try {
            bsns = repoPlugin.list(wildcardFilter);
        } catch (Exception e) {
            logger.logError(MessageFormat.format("Error querying repository {0}.", repoPlugin.getName()), e);
        }
        if (bsns != null) {
            Collections.sort(bsns);
            result = new RepositoryBundle[bsns.size()];
            int i = 0;
            for (String bsn : bsns) {
                result[i++] = new RepositoryBundle(repoPlugin, bsn);
            }
        }
        return result;
    }

    private Object[] searchR5Repository(Repository osgiRepo) {
        Object[] result;
        Set<RepositoryResourceElement> resultSet = new LinkedHashSet<RepositoryResourceElement>();
        Map<Requirement,Collection<Capability>> providers = osgiRepo.findProviders(Collections.singleton(requirementFilter));

        for (Entry<Requirement,Collection<Capability>> providersEntry : providers.entrySet()) {
            for (Capability providerCap : providersEntry.getValue())
                resultSet.add(new RepositoryResourceElement(providerCap.getResource()));
        }

        result = resultSet.toArray(new Object[resultSet.size()]);
        return result;
    }
}
