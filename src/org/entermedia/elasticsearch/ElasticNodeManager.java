package org.entermedia.elasticsearch;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dom4j.Element;
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryRequestBuilder;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotRequestBuilder;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsRequestBuilder;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotRequestBuilder;
import org.elasticsearch.action.admin.indices.close.CloseIndexRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.openedit.entermedia.cluster.NodeManager;

import com.openedit.OpenEditException;
import com.openedit.page.Page;
import com.openedit.util.PathUtilities;
import com.openedit.util.Replacer;

public class ElasticNodeManager extends NodeManager
{
	protected Client fieldClient;
	protected boolean fieldShutdown = false;
	
	public Client getClient()
	{
		if( fieldShutdown == false && fieldClient == null)
		{
			synchronized (this)
			{
				if( fieldClient != null)
				{
					return fieldClient;
				}
				NodeBuilder nb = NodeBuilder.nodeBuilder();//.client(client)local(true);
				
				Page config = getPageManager().getPage("/WEB-INF/node.xml");
				if( !config.exists() )
				{
					throw new OpenEditException("Missing " + config.getPath());
				}

				
				for (Iterator iterator = getLocalNode().getElement().elementIterator("property"); iterator.hasNext();)
				{
					Element	prop = (Element) iterator.next();
					String key = prop.attributeValue("id");
					String val = prop.getTextTrim();

					val = getSetting(key);
					
					nb.settings().put(key, val);
				}
				//extras
	            //nb.settings().put("index.store.type", "mmapfs");
	            //nb.settings().put("index.store.fs.mmapfs.enabled", "true");
	            //nb.settings().put("index.merge.policy.merge_factor", "20");
	           // nb.settings().put("discovery.zen.ping.unicast.hosts", "localhost:9300");
	           // nb.settings().put("discovery.zen.ping.unicast.hosts", elasticSearchHostsList);
	
	            fieldClient = nb.node().client();   //when this line executes, I get the error in the other node 
			}
		}
		return fieldClient;
	}
	
	protected String getSetting(String inId) 
	{
		Page config = getPageManager().getPage("/WEB-INF/node.xml");		
		String abs = config.getContentItem().getAbsolutePath();
		File parent = new File(abs);
		Map params = new HashMap();
		params.put("webroot", parent.getParentFile().getParentFile().getAbsolutePath());
		params.put("nodeid", getLocalNodeId());
		Replacer replace = new Replacer();
		
		String value = getLocalNode().get(inId);
		if( value == null)
		{
			return null;
		}
		if( value.startsWith("."))
		{
			value = PathUtilities.resolveRelativePath(value, abs );
		}
		
		return replace.replace(value, params);
	}

	public void shutdown()
	{
		if(!fieldShutdown)
		{
			getClient().close();
		}
		fieldShutdown = true;
		
	}


	protected String toId(String inId)
	{
		String id = inId.replace('/', '_');
		return id;
	}
	
	public String createSnapShot(String inCatalogId)
	{
		
		String indexid = toId(inCatalogId);
		String path = getSetting("repo.root.location") + "/" + indexid;
		
		//log.info("Deleted nodeid=" + id + " records database " + getSearchType() );
		
		    Settings settings = ImmutableSettings.builder()
		            .put("location", path)
		            .build();

		    String reponame = indexid + "_repo";
		    
		    PutRepositoryRequestBuilder putRepo = 
		    		new PutRepositoryRequestBuilder(getClient().admin().cluster());
		    putRepo.setName(reponame)
		            .setType("fs")
		            .setSettings(settings)
		            .execute().actionGet();

		    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
		    
		    CreateSnapshotRequestBuilder builder = new CreateSnapshotRequestBuilder(getClient().admin().cluster());
		    String snapshotid =  format.format(new Date());
		    builder.setRepository(reponame)
		            .setIndices(indexid)
		            .setWaitForCompletion(true)
		            .setSnapshot(snapshotid);
		    builder.execute().actionGet();
		
		    return snapshotid;
	}
	
	public List listSnapShots(String inCatalogId)
	{
		String indexid = toId(inCatalogId);
	    String reponame = indexid + "_repo";
	    
	    String path = getSetting("repo.root.location");
	    
	    if (!new File(path).exists()) {
	    	return Collections.emptyList();
	    }
	    
		GetSnapshotsRequestBuilder builder = 
		            new GetSnapshotsRequestBuilder(getClient().admin().cluster());
	    builder.setRepository(reponame);
	    GetSnapshotsResponse getSnapshotsResponse = builder.execute().actionGet();
	    List results =  new ArrayList(getSnapshotsResponse.getSnapshots());
	
	    Collections.sort(results, new Comparator<SnapshotInfo>()
		{
	    	@Override
	    	public int compare(SnapshotInfo inO1, SnapshotInfo inO2)
	    	{
	    		return inO1.name().toLowerCase().compareTo(inO2.name().toLowerCase());
	    	}
		});
	    Collections.reverse(results);
	    return results;
	}
	public void restoreSnapShot(String inCatalogId, String inSnapShotId)
	{
		String indexid = toId(inCatalogId);
	    String reponame = indexid + "_repo";

		   // Obtain the snapshot and check the indices that are in the snapshot
	    GetSnapshotsRequestBuilder builder = new GetSnapshotsRequestBuilder(getClient().admin().cluster());
	    builder.setRepository(reponame);
	    builder.setSnapshots(inSnapShotId);
	    GetSnapshotsResponse getSnapshotsResponse = builder.execute().actionGet();
	
	    
	    //TODO: Close index!!
	    
	    // Check if the index exists and if so, close it before we can restore it.
	    ImmutableList indices = getSnapshotsResponse.getSnapshots().get(0).indices();
	    CloseIndexRequestBuilder closeIndexRequestBuilder =
	            new CloseIndexRequestBuilder(getClient().admin().indices());
	    closeIndexRequestBuilder.setIndices(indexid);
	    closeIndexRequestBuilder.execute().actionGet();
	
	    // Now execute the actual restore action
	    RestoreSnapshotRequestBuilder restoreBuilder = new RestoreSnapshotRequestBuilder(getClient().admin().cluster());
	    restoreBuilder.setRepository(reponame).setSnapshot(inSnapShotId);
	    restoreBuilder.execute().actionGet();
	}
}
