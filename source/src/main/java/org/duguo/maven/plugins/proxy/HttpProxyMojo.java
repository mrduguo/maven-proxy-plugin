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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.repository.Repository;
import org.duguo.maven.plugins.proxy.jetty.DownloadRequestHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.sonatype.aether.connector.wagon.WagonProvider;

/**
 * Running as general http proxy server to access resources via wagon.
 * 
 * @goal http
 * @requiresProject false
 */
public class HttpProxyMojo extends AbstractMojo {

	/**
	 * Directory where the upload-bundle will be created.
	 * 
	 * @parameter default-value="${basedir}"
	 * @readonly
	 */
	protected String basedir;

	/**
	 * POM
	 * 
	 * @parameter expression="${project}"
	 * @readonly
	 * @required
	 */
	protected MavenProject project;

	/**
	 * @parameter default-value="${settings}"
	 * @readonly
	 */
	protected Settings settings;

	/**
	 * Host name of the proxy server
	 * 
	 * @parameter expression="${hostname}" default-value="localhost"
	 */
	protected String hostname;

	/**
	 * Listen port of the proxy server
	 * 
	 * @parameter expression="${port}" default-value="8080"
	 */
	protected int port;

	/**
	 * Releases repository storage base
	 * 
	 * @parameter expression="${doNotCopyLocalRepoFile}" default-value="false"
	 */
	protected boolean doNotCopyLocalRepoFile;

	/**
	 * Temporary folder to hold index file for folder list request
	 * 
	 * @parameter expression="${tempDir}" default-value="${basedir}/target/tmp"
	 */
	protected File tempDir;

	/**
	 * WagonProvider to access resources 
	 * 
	 * @component role="org.sonatype.aether.connector.wagon.WagonProvider"
	 * @required
	 * @readonly
	 */
	protected WagonProvider wagonProvider;

	/**
	 * Virtual repository exposed by this proxy.
	 * 
	 * @parameter expression="${virtualRepositories}" 
	 */
	protected List<VirtualRepository> virtualRepositories;

	public void execute() throws MojoExecutionException {
		validateConfiguration();
		try {
			System.out.println("proxy starting");
			Server server = new Server(new InetSocketAddress(hostname, port));
			ContextHandler context = new ContextHandler();
			context.setContextPath("/");
			context.setResourceBase(".");
			context.setClassLoader(Thread.currentThread()
					.getContextClassLoader());
			server.setHandler(context);
			context.setHandler(new DownloadRequestHandler(this));

			server.start();
			System.out.println("proxy started");
			// server.join();

		} catch (Exception e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}

		try {
			// server.join();

			Thread.sleep(1000000);
			System.out.println("proxy stoping");

		} catch (Exception e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}
	}

	protected void validateConfiguration() throws MojoExecutionException {	
		if(virtualRepositories==null || virtualRepositories.size()==0){
			throw new MojoExecutionException("No virtual repository configured!");
		}
	}

	public File resolveArtifact(String requestPath) throws IOException {
		for(VirtualRepository virtualRepository:virtualRepositories){
			if (requestPath.startsWith(virtualRepository.getRequestPrefix())) {
				return downloadResource(virtualRepository,requestPath.substring(virtualRepository.getRequestPrefix().length()));
			}
		}
		
		return listRootVirtualRepos();
	}

	protected File downloadResource(VirtualRepository virtualRepository, String artifactPath) throws IOException {
		if(artifactPath.length()>0){
			artifactPath=artifactPath.substring(1);
		}
		if (artifactPath.endsWith("/")) {
			return listFolder(virtualRepository, artifactPath);
		} else if (artifactPath.lastIndexOf('/')>=artifactPath.lastIndexOf('.')) {
			return listFolder(virtualRepository, artifactPath+"/");
		} else {
			File requestedFile = new File(virtualRepository.getStorageBase(), artifactPath);
			if(requestedFile.isFile()){
				if(virtualRepository.getCacheSeconds()<=0 || (System.currentTimeMillis()-requestedFile.lastModified()-virtualRepository.getCacheSeconds()<=0)){
					return requestedFile;
				}
			}
			requestedFile = downloadSingleFile(virtualRepository,artifactPath);
			return requestedFile;
		}
	}

	protected File listFolder(VirtualRepository virtualRepository, String artifactPath) throws IOException {
		File requestedFile = downloadSingleFile(virtualRepository,artifactPath+"index.html");
		if(requestedFile!=null){
			return requestedFile;
		}
		
		List<String> folderChildList = new ArrayList<String>();
		for (Repository repository : virtualRepository.getDownloadRepositories()) {
			Wagon wagon = connectToWagonRepository(repository);
			if(wagon!=null){
				try {
					if (wagon.resourceExists(artifactPath)) {
						for (Object currentItem : wagon.getFileList(artifactPath)) {
							if (!folderChildList.contains(currentItem)) {
								folderChildList.add((String) currentItem);
							}
						}
					}
				} catch (Exception e) {
					getLog().warn("List repository folder ["+artifactPath+"] failed: "+e.getMessage());
				}
			}
			
		}



		Collections.sort(folderChildList);
		String displayPath=virtualRepository.getRequestPrefix();
		if(artifactPath.length()==1){
			displayPath+="/";
		}else{
			displayPath+="/"+artifactPath;
		}
		

		StringBuffer indexString=new StringBuffer();
		indexString.append("<html><head><title>Index of " + displayPath
				+ " </title></head><body><h2>Index of " + displayPath
				+ "</h2><hr/>");
		indexString.append("<a href=\"../\">../</a><br/>");
		
		if (folderChildList.size() > 0) {
			for (String file : folderChildList) {
				if (file.endsWith("/")) {
					indexString.append("<a href=\"" + file + "\">" + file
							+ "</a><br/>");
				}
			}
			for (String file : folderChildList) {
				if (!file.endsWith("/")) {
					indexString.append("<a href=\"" + file + "\">" + file
							+ "</a><br/>");
				}
			}
		}
		indexString.append("<hr></body></html>");
		

		File indexHtml = writeToTempIndexFile(indexString);		
		return indexHtml;
	}
	


	private File listRootVirtualRepos() throws IOException {
		StringBuffer indexString=new StringBuffer();
		indexString.append("<html><head><title>Virtual repositories list</title></head><body><h2>Virtual repositories list</h2><hr/>");
		for(VirtualRepository virtualRepository:virtualRepositories){
			indexString.append("<a href=\"" + virtualRepository.getRequestPrefix() + "/\">" + virtualRepository.getRequestPrefix()
					+ "</a><br/>");

			if(virtualRepository.getStorageBase()!=null){
				indexString.append("&nbsp;&nbsp;Storage Base: "+virtualRepository.getStorageBase()+"<br/>");
			}
			if(virtualRepository.getCacheSeconds()>0){
				indexString.append("&nbsp;&nbsp;Cache Seconds: "+virtualRepository.getCacheSeconds()+"<br/>");
			}

			if(virtualRepository.getIgnoredRepositories().size()>0){
				indexString.append("&nbsp;&nbsp;Ignored Repositories<br/>");
				for(Repository repository:virtualRepository.getIgnoredRepositories()){
					indexString.append("&nbsp;&nbsp;&nbsp;&nbsp;"+repository.getId()+": ");
					indexString.append("<a href=\""+repository.getUrl()+"\">"+repository.getUrl()+"</a><br/>");
				}
			}

			indexString.append("&nbsp;&nbsp;Download Repositories<br/>");
			for(Repository repository:virtualRepository.getDownloadRepositories()){
				indexString.append("&nbsp;&nbsp;&nbsp;&nbsp;"+repository.getId()+": ");
				indexString.append("<a href=\""+repository.getUrl()+"\">"+repository.getUrl()+"</a><br/>");				
			}
		}
		indexString.append("<hr></body></html>");
		File indexHtml = writeToTempIndexFile(indexString);		
		return indexHtml;
	}

	private File writeToTempIndexFile(StringBuffer indexString) throws IOException {
		if (!tempDir.exists()) {
			tempDir.mkdirs();
		}
		File indexHtml = File.createTempFile("repo.", ".tmp.index.html",tempDir);
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(indexHtml));
			out.write(indexString.toString());
			out.close();
		}
		catch (IOException e){
			throw new RuntimeException("failed to write temp index file",e);
		}
		return indexHtml;
	}

	protected Wagon connectToWagonRepository(Repository repository) {
		try {
			String wagonHint = repository.getProtocol().toLowerCase(Locale.ENGLISH);
			Wagon wagon = wagonProvider.lookup(wagonHint);
			wagon.connect(repository, getAuthenticationInfo(repository.getId()));
			return wagon;
		} catch (Exception e) {
			getLog().warn("Unable to connect to wagon repository ["+repository.getId()+":"+repository.getUrl()+"]: "+e.getMessage(), e);
			return null;
		}
	}

	protected File downloadSingleFile(VirtualRepository virtualRepository,String artifactPath) {
		for (Repository repository : virtualRepository.getIgnoredRepositories()) {
			Wagon wagon = connectToWagonRepository(repository);
			if(wagon!=null){
				try {
					if (wagon.resourceExists(artifactPath)) {
						getLog().info("Artifact ["+artifactPath+"] already existing in repository ["+repository.getId()+"]");
						return null;
					}
				} catch (Exception e) {
					getLog().warn("Check ignored repo for ["+artifactPath+"] failed: "+e.getMessage());
				}
			}
		}			
		
		for (Repository repository : virtualRepository.getDownloadRepositories()) {
			String wagonHint = repository.getProtocol().toLowerCase(Locale.ENGLISH);
			if(doNotCopyLocalRepoFile && wagonHint.equals("file")){
				File destination = new File(repository.getBasedir(), artifactPath);
				if(destination.isFile()){
					return destination;
				}
			}else{
				Wagon wagon = connectToWagonRepository(repository);
				if(wagon!=null){
					try {
						if (wagon.resourceExists(artifactPath)) {
							File destination = new File(virtualRepository.getStorageBase(), artifactPath);
							destination = performRemoteDownload( wagon,artifactPath, destination);
							return destination;
						}
					} catch (Exception e) {
						getLog().warn("Download artifact ["+artifactPath+"] failed: "+e.getMessage());
					}
				}
				
			}
		}
		return null;
	}

	protected File performRemoteDownload(Wagon wagon,String artifactPath, File destination) throws Exception {
		File tmpFile=new File(destination.getAbsolutePath()+"."+System.currentTimeMillis());
		getLog().debug("Downloading "+wagon.getRepository().getUrl()+"/"+artifactPath);
		wagon.get(artifactPath, tmpFile);
		if(destination.exists()){
			destination.delete();
		}
		tmpFile.renameTo(destination);
		getLog().info("Downloaded "+wagon.getRepository().getUrl()+"/"+artifactPath);
		return destination;
	}

	protected AuthenticationInfo getAuthenticationInfo(String repositoryId) {
		AuthenticationInfo auth = null;
		org.apache.maven.settings.Server mavenServerModel = settings.getServer(repositoryId);
		if (mavenServerModel != null) {
			auth = new AuthenticationInfo();
			auth.setUserName(mavenServerModel.getUsername());
			auth.setPassword(mavenServerModel.getPassword());
			auth.setPrivateKey(mavenServerModel.getPrivateKey());
			auth.setPassphrase(mavenServerModel.getPassphrase());
		}
		return auth;
	}
}
