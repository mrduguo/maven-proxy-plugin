package org.duguo.maven.plugins.proxy;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.repository.Repository;
import org.sonatype.aether.repository.RemoteRepository;

/**
 * Running as maven repository proxy with auto detected configuration based on current maven runtime environment.
 * 
 * @goal repoauto
 * @requiresProject false
 */
public class RepoAutoMojo extends RepoMavenMojo {

	/**
	 * Releases repository prefix
	 * 
	 * @parameter expression="${releasesRepositoryPrefix}"
	 *            default-value="/releases"
	 */
	protected String releasesRepositoryPrefix;

	/**
	 * Releases repository storage base
	 * 
	 * @parameter expression="${releasesRepositoryBase}"
	 *            default-value="${basedir}/target/repos/releases"
	 */
	protected String releasesRepositoryBase;

	/**
	 * Plugins repository prefix
	 * 
	 * @parameter expression="${pluginsRepositoryPrefix}"
	 *            default-value="/plugins"
	 */
	protected String pluginsRepositoryPrefix;

	/**
	 * Plugins repository storage base
	 * 
	 * @parameter expression="${pluginsRepositoryBase}"
	 *            default-value="${basedir}/target/repos/plugins"
	 */
	protected String pluginsRepositoryBase;

	/**
	 * The file will be checked to indicate the central repo is accessible
	 * 
	 * @parameter expression="${cetralRepoValidationResource}" default-value="/org/apache/maven/plugins/maven-metadata.xml" 
	 */
	protected String cetralRepoValidationResource;

	/**
	 * Central repository url
	 * 
	 * @parameter expression="${cetralRepoUrl}" default-value="http://repo1.maven.org/maven2" 
	 */
	protected String cetralRepoUrl;
	
	/**
	 * Central repository uk mirror url
	 * 
	 * @parameter expression="${cetralMirrorRepoUrl}" default-value="http://uk.maven.org/maven2" 
	 */
	protected String cetralMirrorRepoUrl;

	/**
	 * Use mirror for central server
	 * 
	 * @parameter expression="${useMirrorForCentralServer}" default-value="false" 
	 */
	protected boolean useMirrorForCentralServer;
	

	/**
	 * java.net repository url
	 * 
	 * @parameter expression="${javaNetRepoUrl}" default-value="http://download.java.net/maven/2" 
	 */
	protected String javaNetRepoUrl;

	/**
	 * add project remote repos as ignored repositories, check first before go to proxy repo.
	 * 
	 * @parameter expression="${addProjectRemoteReposAsIgnored}" default-value="true" 
	 */
	protected boolean addProjectRemoteReposAsIgnored;
	
	protected void validateConfiguration() throws MojoExecutionException {
		if(virtualRepositories==null){
			virtualRepositories=new ArrayList<VirtualRepository>();
		}
		addProjectRemoteRepos(releasesRepositoryPrefix,releasesRepositoryBase, convertArtifactToWagonRepos(project.getRemoteArtifactRepositories()));
		addProjectRemoteRepos(pluginsRepositoryPrefix,pluginsRepositoryBase,convertPluginReposToWagonRepos(project.getRemotePluginRepositories()));
		displayVirtualRepositoriesConfig();
		super.validateConfiguration();
	}


	protected List<Repository> convertArtifactToWagonRepos(List<ArtifactRepository> remoteArtifactRepositories) {
		List<Repository> wagonRepos=new ArrayList<Repository>();
		for(ArtifactRepository artifactRepository: remoteArtifactRepositories){
			if(artifactRepository.getReleases().isEnabled()){
				addWagonOnlyIfSupportedProtocol(wagonRepos,artifactRepository.getId(),artifactRepository.getUrl());
			}
		}
		return wagonRepos;
	}

	protected List<Repository> convertPluginReposToWagonRepos(List<RemoteRepository> remotePluginRepositories) {
		List<Repository> wagonRepos=new ArrayList<Repository>();
		for(RemoteRepository remoteRepository: remotePluginRepositories){
			if(remoteRepository.getPolicy(false).isEnabled()){ //skip snapshot only repo
				addWagonOnlyIfSupportedProtocol(wagonRepos,remoteRepository.getId(),remoteRepository.getUrl());
			}
		}
		return wagonRepos;
	}

	protected void addWagonOnlyIfSupportedProtocol(List<Repository> wagonRepos,String repoId, String repoUrl) {
		Repository wagonRepo=new Repository(repoId,repoUrl);
		try {
			String wagonHint = wagonRepo.getProtocol().toLowerCase(Locale.ENGLISH);
			wagonProvider.lookup(wagonHint);
			wagonRepos.add(wagonRepo);
		} catch (Exception e) {
			getLog().warn("Unsupported wagon protocol for repository ["+repoId+":"+repoUrl+"]: "+e.getMessage());
		}
	}

	protected void addProjectRemoteRepos(String repoPrefix,String repoBase,List<Repository> wagonRepos) {
		VirtualRepository virtualRepository=new VirtualRepository();
		virtualRepository.setRequestPrefix(repoPrefix);
		virtualRepository.setStorageBase(repoBase);
		
		for(Repository wagonRepo:wagonRepos){
			if (wagonRepo.getId().toLowerCase().contains("proxy")) {
				virtualRepository.getDownloadRepositories().add(wagonRepo);
			}
		}
		
		
		if(virtualRepository.getDownloadRepositories().size()==0){ 
			// no explicitly defined proxy repo, will use central repo as download repo
			for(Repository wagonRepo:wagonRepos){
				if (wagonRepo.getId().equals("central")) {
					Repository centralRepository=wagonRepo;
					if(!isCentralRepositoryAccessable(centralRepository)){
						String repoUrl;
						if(useMirrorForCentralServer){
							repoUrl=cetralMirrorRepoUrl;
						}else{
							repoUrl=cetralRepoUrl;
						}
						centralRepository=new Repository("central",repoUrl);
					}	
					virtualRepository.getDownloadRepositories().add(centralRepository);
					getLog().info("use central repo ["+centralRepository.getUrl()+"] as proxy for ["+repoPrefix+"]");
					
					if(!javaNetRepoUrl.equals("disabled")){
						Repository javaNetRepository=new Repository("java.net",javaNetRepoUrl);
						virtualRepository.getDownloadRepositories().add(javaNetRepository);
					}
					
				}
			}
		}
		
		if(addProjectRemoteReposAsIgnored){
			for(Repository wagonRepo:wagonRepos){
				if (!virtualRepository.getDownloadRepositories().contains(wagonRepo)) {
					virtualRepository.getIgnoredRepositories().add(wagonRepo);
				}
			}
		}else{
			int i=0;
			for(Repository wagonRepo:wagonRepos){
				if (!virtualRepository.getDownloadRepositories().contains(wagonRepo)) {				
					virtualRepository.getDownloadRepositories().add(i,wagonRepo);
					i++;
				}
			}
		}
		
		virtualRepositories.add(virtualRepository);
	}

	protected boolean isCentralRepositoryAccessable(Repository wagonRepo) {
		Wagon wagon=connectToWagonRepository(wagonRepo);
		if(wagon!=null){
			try {
				if (wagon.resourceExists(cetralRepoValidationResource)) {
					return true;
				}
			} catch (Exception e) {
				getLog().warn("Verify central repo failed: "+e.getMessage());
			}
		}
		return false;
	}

	private void displayVirtualRepositoriesConfig() {
		getLog().info("Auto generated virtual repositories:");
		System.out.println("\n===========================================");
		System.out.println("<virtualRepositories>");
		for(VirtualRepository virtualRepository:virtualRepositories){
			System.out.println("  <virtualRepository>");
			System.out.println("      <requestPrefix>"+virtualRepository.getRequestPrefix()+"</requestPrefix>");
			if(virtualRepository.getStorageBase()!=null){
				System.out.println("      <storageBase>"+virtualRepository.getStorageBase()+"</storageBase>");				
			}
			if(virtualRepository.getCacheSeconds()>0){
				System.out.println("      <cacheSeconds>"+virtualRepository.getCacheSeconds()+"</cacheSeconds>");
			}
			
			if(virtualRepository.getIgnoredRepositories().size()>0){
				System.out.println("      <ignoredRepositories>");
				for(Repository repository:virtualRepository.getIgnoredRepositories()){
					System.out.println("        <ignoredRepository>");
					System.out.println("          <id>"+repository.getId()+"</id>");
					System.out.println("          <url>"+repository.getUrl()+"</url>");
					System.out.println("        </ignoredRepository>");				
				}
				System.out.println("      </ignoredRepositories>");
			}
			
			System.out.println("      <downloadRepositories>");
			for(Repository repository:virtualRepository.getDownloadRepositories()){
				System.out.println("        <downloadRepository>");
				System.out.println("          <id>"+repository.getId()+"</id>");
				System.out.println("          <url>"+repository.getUrl()+"</url>");
				System.out.println("        </downloadRepository>");				
			}
			System.out.println("      </downloadRepositories>");
			
			System.out.println("  </virtualRepository>");
		}
		System.out.println("</virtualRepositories>");
		System.out.println("===========================================\n");
	}

}
