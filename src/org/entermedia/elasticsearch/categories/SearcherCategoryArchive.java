package org.entermedia.elasticsearch.categories;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.openedit.data.Searcher;
import org.openedit.data.SearcherManager;
import org.openedit.entermedia.Category;
import org.openedit.entermedia.CategoryArchive;
import org.openedit.entermedia.xmldb.XmlCategoryArchive;

import com.openedit.ModuleManager;
import com.openedit.OpenEditException;
import com.openedit.util.PathUtilities;

/**
 * Archive that does not cache and uses a database for storage
 * This is not specific to ElasticSearch. Can be used with any backing CategorySearcher
 * @author cburkey
 */

public class SearcherCategoryArchive implements CategoryArchive 
{
	protected String fieldCatalogId;
	protected SearcherManager fieldSearcherManager;
	protected ModuleManager fieldModuleManager;

	protected CategoryArchive fieldBackingArchive;
	protected Searcher fieldCategorySearcher;
	protected XmlCategoryArchive fieldXmlCategoryArchive;

	public ModuleManager getModuleManager() {
		return fieldModuleManager;
	}


	public void setModuleManager(ModuleManager inModuleManager) {
		fieldModuleManager = inModuleManager;
	}

	
	
	public CategoryArchive getBackingArchive() {
		if (fieldBackingArchive == null)
		{
			fieldBackingArchive = (CategoryArchive) getModuleManager().getBean(getCatalogId(), "categoryArchive");
			fieldBackingArchive.setCatalogId(getCatalogId());
		}
		return fieldBackingArchive;
	}


	public SearcherManager getSearcherManager() {
		return fieldSearcherManager;
	}

	public void setSearcherManager(SearcherManager inSearcherManager) {
		fieldSearcherManager = inSearcherManager;
	}

	public String getCatalogId() {
		return fieldCatalogId;
	}

	public void setCatalogId(String inCatalogId) {
		fieldCatalogId = inCatalogId;
	}


	public Searcher getCategorySearcher() 
	{
		if (fieldCategorySearcher == null) {
			fieldCategorySearcher = getSearcherManager().getSearcher(getCatalogId(), "category");
		}
		return fieldCategorySearcher;
	}


	@Override //clears cache. Do nothing
	public void clearCategories() {
		reloadCategories();
	}

	@Override
	public void reloadCategories() 
	{
		getBackingArchive().reloadCategories();
		//Reload entire Index
		getCategorySearcher().deleteAll(null);
		List categories = getBackingArchive().listAllCategories();
		for (Iterator iterator = categories.iterator(); iterator.hasNext();) {
			Category cat = (Category) iterator.next();
			getCategorySearcher().saveData(cat, null);
		}
	}


	@Override
	public void saveAll() 
	{
		getBackingArchive().setRootCategory(getRootCategory());
		getBackingArchive().saveAll();
	}
	@Override
	public Category getCategory(String inCategory) 
	{
		if( inCategory == null)
		{
			return null;
		}
		if( "index".equals(inCategory))
		{
			return getRootCategory();
		}
		Category category = (Category)getCategorySearcher().searchById(inCategory);
		return category;
	}

	public Category addChild(Category inCategory)
	{
		getRootCategory().addChild(inCategory);
		saveCategory(inCategory);
		return inCategory;
	}
	
	@Override
	public void deleteCategory(Category inCategory) 
	{
		getCategorySearcher().delete(inCategory, null);
		getBackingArchive().deleteCategory(inCategory);

	}

	@Override
	public void setRootCategory(Category inRoot) 
	{
		inRoot.setParentCategory(null);
		saveCategory(inRoot);
	}

	@Override
	public Category getRootCategory() 
	{
		Category root = (Category)getCategorySearcher().searchById("index");
		if( root == null)
		{
			reloadCategories();
			root = (Category)getCategorySearcher().searchById("index");
		}
		return root;
	}



	@Override
	public void saveCategory(Category inCategory) 
	{
		//Category parent = inCategory.getParentCategory();
		getCategorySearcher().saveData(inCategory, null);
		
		
		/* this code is too complex. Just going to write out the entire DB
		//replace the tree with this ones parent
		if( parent == null)
		{
			parent= getRootCategory(); //root node
		}
		
		Category copy = getBackingArchive().getCategory(parent.getId());
		if( copy == null)
		{
			//This is a new catalog. Need to save out a starting point
			getBackingArchive().setRootCategory(parent);
			getBackingArchive().saveAll();
		}
		else
		{
			//Copy the data into the XML version of this one node.
			//This allows us to speed up the save without pulling more stuff from the DB
			copy.setChildren(parent.getChildren());
			copy.setProperties(new HashMap(parent.getProperties()));
			copy.removeProperty("parentid");
			copy.setName(parent.getName());
			getBackingArchive().saveCategory(copy);
		}
		*/
		getBackingArchive().setRootCategory(getRootCategory());
		getBackingArchive().saveAll();
	}

	public Category createNewCategory(String inLabel)
	{
		String id = createCategoryId(inLabel);
		Category cat = (Category)getCategorySearcher().createNewData();
		cat.setId(id);
		cat.setName(inLabel);
		return cat;
	}
	
	
	protected String createCategoryId(String inPath)
	{
		// subtract the start /store/assets/stuff/more -> stuff_more
		if (inPath.length() < 0)
		{
			return "index";
		}

		if (inPath.startsWith("/"))
		{
			inPath = inPath.substring(1);
		}
		inPath = inPath.replace('/', '_');
		String id = PathUtilities.extractId(inPath, true);
		return id;
	}


	public Category createCategoryTree(String inPath) throws OpenEditException
	{
		Category newcat = createCategoryTree(inPath, null);
		return newcat;
	}

	public Category createCategoryTree(String inPath, List inNames) throws OpenEditException
	{

		if (inPath.length() < 1)
		{
			return getRootCategory();
		}

		if (inPath.endsWith("/"))
		{
			inPath = inPath.substring(0, inPath.length() - 1);
		}

		String catid = createCategoryId(inPath);
		Category child = getCategory(catid);
		if (child == null)
		{
			// make sure we have a parent to put it in
			child = (Category)getCategorySearcher().createNewData();
			child.setId(catid);

			if (inNames != null)
			{
				child.setName((String) inNames.remove(inNames.size() - 1));
			}
			else
			{
				child.setName(PathUtilities.extractFileName(inPath));
			}
			String parentPath = PathUtilities.extractDirectoryPath(inPath);
			if (parentPath == null || parentPath == "/" || parentPath.length() == 0)
			{
				return child;
			}

			Category inParentCategory = createCategoryTree(parentPath, inNames);
			inParentCategory.addChild(child); //It is going to be saved to root 
			saveCategory(child);
		}
		return child;
	}


	public Category getCategoryByName(String inCategoryName) 
	{
		throw new IllegalArgumentException("Method not yet implemented");
	}

	public List listAllCategories()
	{
		List all = new ArrayList();
		addCategories(all, getRootCategory());
		return all;
	}
	private void addCategories(List inAll, Category inRootCatalog)
	{
		// TODO Auto-generated method stub
		inAll.add(inRootCatalog);
		for (Iterator iter = inRootCatalog.getChildren().iterator(); iter.hasNext();)
		{
			Category child = (Category) iter.next();
			addCategories(inAll, child);
		}
	}

	@Override
	public Category cacheCategory(Category inCategory) {
		//the category is saved but we wanted to update the local map. Not needed for real time Elastic backed systems
		return null;
	}

}
