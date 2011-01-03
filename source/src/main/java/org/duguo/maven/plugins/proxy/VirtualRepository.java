package org.duguo.maven.plugins.proxy;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.wagon.repository.Repository;

public class VirtualRepository {

	/**
	 * The http request context path without artifact path. For given sample,
	 * the requestPrefix will be /releases
	 * http://localhost:8080/releases/org/slf4j
	 * /slf4j-api/1.6.1/slf4j-api-1.6.1.pom
	 */
	private String requestPrefix;

	/**
	 * Local storage base path, e.g. default storageBase for releases:
	 * ${basedir}/target/repos/releases
	 */
	private String storageBase;

	/**
	 * How long the downloaded artifact should be refreshed. Only set to
	 * positive number for snapshots repository when desired.
	 */
	private long cacheSeconds = 0;

	/**
	 * If artifcat found in ignored repositories, will return 404 to client to
	 * avoid download from this proxy server
	 */
	private List<Repository> ignoredRepositories=new ArrayList<Repository>();

	/**
	 * Repositories to download requested artifact
	 */
	private List<Repository> downloadRepositories=new ArrayList<Repository>();

	public String getRequestPrefix() {
		return requestPrefix;
	}

	public void setRequestPrefix(String requestPrefix) {
		this.requestPrefix = requestPrefix;
	}

	public String getStorageBase() {
		return storageBase;
	}

	public void setStorageBase(String storageBase) {
		this.storageBase = storageBase;
	}

	public long getCacheSeconds() {
		return cacheSeconds;
	}

	public void setCacheSeconds(long cacheSeconds) {
		this.cacheSeconds = cacheSeconds;
	}

	public List<Repository> getIgnoredRepositories() {
		return ignoredRepositories;
	}

	public void setIgnoredRepositories(List<Repository> ignoredRepositories) {
		this.ignoredRepositories = ignoredRepositories;
	}

	public List<Repository> getDownloadRepositories() {
		return downloadRepositories;
	}

	public void setDownloadRepositories(List<Repository> downloadRepositories) {
		this.downloadRepositories = downloadRepositories;
	}
}
