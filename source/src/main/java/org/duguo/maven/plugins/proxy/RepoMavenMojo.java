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

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.wagon.Wagon;

/**
 * Running as maven repository proxy with explicit repository configuration. Will download additional resource such as checksum and java doc.
 * 
 * @goal repomaven
 * @requiresProject false
 */
public class RepoMavenMojo extends HttpProxyMojo {


	/**
	 * Checksum file extensions, defaults include .md5 and .sha1
	 * 
	 * @parameter expression="${checksumExtensions}"
	 */
	protected String[] checksumExtensions;
	

	/**
	 * Checksum file extensions, defaults include sources and javadoc
	 * 
	 * @parameter expression="${jarArtifactAdditionalClassifier}"
	 */
	protected String[] jarArtifactAdditionalClassifier;


	protected void validateConfiguration() throws MojoExecutionException {	
		if(checksumExtensions==null || checksumExtensions.length==0){
			checksumExtensions=new String[]{".md5",".sha1"};
		}
		if(jarArtifactAdditionalClassifier==null || jarArtifactAdditionalClassifier.length==0){
			jarArtifactAdditionalClassifier=new String[]{"sources","javadoc"};
		}
		super.validateConfiguration();
	}

	protected File downloadSingleFile(VirtualRepository virtualRepository,String artifactPath) {
		File requestedFile = super.downloadSingleFile(virtualRepository,artifactPath);
		if (requestedFile != null && artifactPath.endsWith(".jar")) {
			String baseFileName = requestedFile.getAbsolutePath();
			baseFileName = baseFileName.substring(0,baseFileName.length() - 4);
			if (new File(baseFileName + ".pom").exists()) {
				baseFileName = artifactPath.substring(0, artifactPath.length() - 4);
				for(String classifier:jarArtifactAdditionalClassifier){
					File classifierArtifact = downloadSingleFile(virtualRepository, baseFileName + "-"+classifier+".jar");
					if (classifierArtifact == null) {
						getLog().warn("No classifier ["+classifier+"] found for [" + artifactPath+"]");
					}
				}
			}
		}		
		return requestedFile;
	}


	protected File performRemoteDownload(Wagon wagon,String artifactPath, File destination) throws Exception {
		destination=super.performRemoteDownload(wagon, artifactPath, destination);
		for(String checksumExtension:checksumExtensions){
			if (artifactPath.endsWith(checksumExtension)) {
				return destination;
			}
		}
		for(String checksumExtension:checksumExtensions){
			if (wagon.resourceExists(artifactPath + checksumExtension)) {
				performRemoteDownload(wagon,artifactPath + checksumExtension, new File(destination.getAbsolutePath() + checksumExtension));
			}
		}		
		return destination;
	}

}
