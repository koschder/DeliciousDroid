/*
 * DeliciousDroid - http://code.google.com/p/DeliciousDroid/
 *
 * Copyright (C) 2010 Matt Schmidt
 *
 * DeliciousDroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * DeliciousDroid is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DeliciousDroid; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 */

package com.deliciousdroid.activity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import org.apache.http.auth.AuthenticationException;

import com.deliciousdroid.R;
import com.deliciousdroid.action.BookmarkTaskArgs;
import com.deliciousdroid.client.DeliciousApi;
import com.deliciousdroid.client.NetworkUtilities;
import com.deliciousdroid.platform.BookmarkManager;
import com.deliciousdroid.platform.TagManager;
import com.deliciousdroid.providers.BookmarkContent.Bookmark;
import com.deliciousdroid.providers.ContentNotFoundException;
import com.deliciousdroid.providers.TagContent.Tag;
import com.deliciousdroid.ui.TagSpan;
import com.deliciousdroid.util.StringUtils;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class AddBookmark extends AppBaseActivity{

	private EditText mEditUrl;
	private EditText mEditDescription;
	private ProgressBar mDescriptionProgress;
	private EditText mEditNotes;
	private EditText mEditTags;
	private TextView mRecommendedTags;
	private ProgressBar mRecommendedProgress;
	private TextView mPopularTags;
	private ProgressBar mPopularProgress;
	private TextView mNetworkTags;
	private ProgressBar mNetworkProgress;
	private CheckBox mPrivate;
	private Bookmark bookmark;
	Thread background;
	private Boolean update = false;
	private Resources res;
	
	private Bookmark oldBookmark;
	
	private long updateTime = 0;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.add_bookmark);
		mEditUrl = (EditText) findViewById(R.id.add_edit_url);
		mEditDescription = (EditText) findViewById(R.id.add_edit_description);
		mDescriptionProgress = (ProgressBar) findViewById(R.id.add_description_progress);
		mEditNotes = (EditText) findViewById(R.id.add_edit_notes);
		mEditTags = (EditText) findViewById(R.id.add_edit_tags);
		mRecommendedTags = (TextView) findViewById(R.id.add_recommended_tags);
		mRecommendedProgress = (ProgressBar) findViewById(R.id.add_recommended_tags_progress);
		mPopularTags = (TextView) findViewById(R.id.add_popular_tags);
		mPopularProgress = (ProgressBar) findViewById(R.id.add_popular_tags_progress);
		mNetworkTags = (TextView) findViewById(R.id.add_network_tags);
		mNetworkProgress = (ProgressBar) findViewById(R.id.add_network_tags_progress);
		mPrivate = (CheckBox) findViewById(R.id.add_edit_private);
		
		mRecommendedTags.setMovementMethod(LinkMovementMethod.getInstance());
		mPopularTags.setMovementMethod(LinkMovementMethod.getInstance());
		mNetworkTags.setMovementMethod(LinkMovementMethod.getInstance());
		
		res = getResources();

		if(savedInstanceState ==  null){
			Intent intent = getIntent();
			
			if(Intent.ACTION_SEARCH.equals(intent.getAction())){
				if(intent.hasExtra(SearchManager.QUERY)){
					Intent i = new Intent(mContext, MainSearchResults.class);
					i.putExtras(intent.getExtras());
					startActivity(i);
					finish();
				} else {
					onSearchRequested();
				}
			} else if(Intent.ACTION_VIEW.equals(intent.getAction())) {
				Uri data = intent.getData();
				String path = null;
				String tagname = null;
	
				if(data != null) {
					path = data.getPath();
					tagname = data.getQueryParameter("tagname");
				}

				if(data.getScheme() == null || !data.getScheme().equals("content")){
					Intent i = new Intent(Intent.ACTION_VIEW, data);

					startActivity(i);
					finish();        
				} else if(path.contains("bookmarks") && TextUtils.isDigitsOnly(data.getLastPathSegment())) {
					Intent viewBookmark = new Intent(this, ViewBookmark.class);
					viewBookmark.setData(data);

					Log.d("View Bookmark Uri", data.toString());
					startActivity(viewBookmark);
					finish();
				} else if(tagname != null) {
					Intent viewTags = new Intent(this, BrowseBookmarks.class);
					viewTags.setData(data);

					Log.d("View Tags Uri", data.toString());
					startActivity(viewTags);
					finish();
				}
			} else if(Intent.ACTION_SEND.equals(intent.getAction())){
				String extraData = intent.getStringExtra(Intent.EXTRA_TEXT);
				
				String url = StringUtils.getUrl(extraData);
				
				mEditUrl.setText(url);
				
				new GetWebpageTitleTask().execute(url);
			} else if(Intent.ACTION_EDIT.equals(intent.getAction())){
				int id = Integer.parseInt(intent.getData().getLastPathSegment());
				try {
					Bookmark b = BookmarkManager.GetById(id, mContext);
					oldBookmark = b.copy();
					
					mEditUrl.setText(b.getUrl());
					mEditDescription.setText(b.getDescription());
					mEditNotes.setText(b.getNotes());
					mEditTags.setText(b.getTagString());
					updateTime = b.getTime();
					
					update = true;
				} catch (ContentNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				mEditUrl.requestFocus();				
			}
		}
		
		if(update)
			setTitle("Edit Bookmark");
		else setTitle("Add Bookmark");
		
		mEditUrl.setOnFocusChangeListener(new OnFocusChangeListener(){
			public void onFocusChange(View v, boolean hasFocus) {
				if(!hasFocus){
					String url = mEditUrl.getText().toString();
					
					new GetWebpageTitleTask().execute(url);
					new GetTagSuggestionsTask().execute(url);
				}
			}
		});
	}
	
	public void saveHandler(View v) {
		save();
	}

	public void cancelHandler(View v) {
    	finish();
	}
	
    public void save() {

		String url = mEditUrl.getText().toString();
		
		if(mEditDescription.getText().toString().equals("")) {
			mEditDescription.setText(url);
		}
		
		if(!url.startsWith("http")){
			url = "http://" + url;
		}
		
		if(!update) {
			Date d = new Date();
			updateTime = d.getTime();
		}
		
		bookmark = new Bookmark(url, mEditDescription.getText().toString(), 
			mEditNotes.getText().toString(), mEditTags.getText().toString(),
			mPrivate.isChecked(), updateTime);
		
		BookmarkTaskArgs args = new BookmarkTaskArgs(bookmark, mAccount, mContext);
		
		new AddBookmarkTask().execute(args);
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
	    MenuInflater inflater = getMenuInflater();

		if(result && isMyself()) {
			inflater.inflate(R.menu.add_bookmark_menu, menu);
		}

	    return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	    case R.id.menu_addbookmark_save:
	    	save();
			return true;
	    case R.id.menu_addbookmark_cancel:       	
        	finish();
	        return true;
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}
    
    TagSpan.OnTagClickListener tagOnClickListener = new TagSpan.OnTagClickListener() {
        public void onTagClick(String tag) {
        	String currentTagString = mEditTags.getText().toString();
        	
        	ArrayList<String> currentTags = new ArrayList<String>();
        	Collections.addAll(currentTags, currentTagString.split(" "));
        	
        	if(tag != null && tag != "") {
        		if(!currentTags.contains(tag)) {
		        	currentTags.add(tag);
        		} else {
        			currentTags.remove(tag);
        		}
        		mEditTags.setText(TextUtils.join(" ", currentTags.toArray()).trim());
        	}
        }
    };

    private class AddBookmarkTask extends AsyncTask<BookmarkTaskArgs, Integer, Boolean>{
    	private Context context;
    	private Bookmark bookmark;
    	private Account account;
    	private ProgressDialog progress;
    	
    	@Override
    	protected Boolean doInBackground(BookmarkTaskArgs... args) {
    		context = args[0].getContext();
    		bookmark = args[0].getBookmark();
    		account = args[0].getAccount();
    		
    		try {
    			Boolean success = DeliciousApi.addBookmark(bookmark, account, context);
    			if(success){
    				if(update){
    					BookmarkManager.UpdateBookmark(bookmark, account.name, context);
    				} else {
    					BookmarkManager.AddBookmark(bookmark, account.name, context);
    				}
    				return true;
    			} else return false;
    		} catch (Exception e) {
    			Log.d("addBookmark error", e.toString());
    			return false;
    		}
    	}
    	
        protected void onPreExecute() {
	        progress = new ProgressDialog(mContext);
	        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
	        progress.setMessage("Working...");
	        progress.setCancelable(true);
	        progress.show();
        }

        protected void onPostExecute(Boolean result) {
        	progress.dismiss();
        	
    		if(result){
    			for(Tag t : bookmark.getTags()){   				
    				TagManager.UpsertTag(t, account.name, context);
    			}
    			
    			if(update) {
    				for(Tag t : oldBookmark.getTags()) {
    					if(!bookmark.getTags().contains(t))
    						TagManager.UpleteTag(t, account.name, context);
    				}
    			}
    			
    			String msg = null;
    			if(update)
    				msg = res.getString(R.string.edit_bookmark_success_msg);
    			else msg = res.getString(R.string.add_bookmark_success_msg);
    			
    			Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    		} else {
    			Toast.makeText(context, res.getString(R.string.add_bookmark_error_msg), Toast.LENGTH_SHORT).show();
    		}
    		
    		finish();
        }
    }
    
    public class GetWebpageTitleTask extends AsyncTask<String, Integer, String>{
    	private String url;
    	
    	@Override
    	protected String doInBackground(String... args) {
    		
    		if(args.length > 0 && args[0] != null && args[0] != "") {
	    		url = args[0];
		
	    		return NetworkUtilities.getWebpageTitle(url);
    		} else return "";
    		
    	}
    	
    	protected void onPreExecute(){
    		mDescriptionProgress.setVisibility(View.VISIBLE);
    	}
    	
        protected void onPostExecute(String result) {
        	mEditDescription.setText(Html.fromHtml(result));
        	mDescriptionProgress.setVisibility(View.GONE);
        }
    }
    
    public class GetTagSuggestionsTask extends AsyncTask<String, Integer, ArrayList<Tag>>{
    	private String url;
    	
    	@Override
    	protected ArrayList<Tag> doInBackground(String... args) {
    		url = args[0];
	
    		try {
				return DeliciousApi.getSuggestedTags(url, mAccount, mContext);
			} catch (AuthenticationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
    	}
    	
    	protected void onPreExecute() {
    		mRecommendedTags.setVisibility(View.GONE);
    		mPopularTags.setVisibility(View.GONE);
    		mNetworkTags.setVisibility(View.GONE);
    		mRecommendedProgress.setVisibility(View.VISIBLE);
    		mPopularProgress.setVisibility(View.VISIBLE);
    		mNetworkProgress.setVisibility(View.VISIBLE);
    	}

    	
        protected void onPostExecute(ArrayList<Tag> result) {
        	        	
        	if(result != null) {
        		SpannableStringBuilder recommendedBuilder = new SpannableStringBuilder();
        		SpannableStringBuilder popularBuilder = new SpannableStringBuilder();
        		SpannableStringBuilder networkBuilder = new SpannableStringBuilder();
        		
        		
        		
        		for(Tag t : result) {
        			if(t.getType().equals("recommended")) {
        				addTag(recommendedBuilder, t);
        			} else if(t.getType().equals("popular")) {
        				addTag(popularBuilder, t);
        			} else if(t.getType().equals("network")) {
        				addTag(networkBuilder, t);
        			}
        		}
        		
        		mRecommendedTags.setText(recommendedBuilder);
        		mPopularTags.setText(popularBuilder);
        		mNetworkTags.setText(networkBuilder);
        		
        		mRecommendedTags.setVisibility(View.VISIBLE);
        		mPopularTags.setVisibility(View.VISIBLE);
        		mNetworkTags.setVisibility(View.VISIBLE);
        		mRecommendedProgress.setVisibility(View.GONE);
        		mPopularProgress.setVisibility(View.GONE);
        		mNetworkProgress.setVisibility(View.GONE);

        	} 	
        }

		private void addTag(SpannableStringBuilder builder, Tag t) {
			int flags = 0;
			
			if (builder.length() != 0) {
				builder.append("  ");
			}
			
			int start = builder.length();
			builder.append(t.getTagName());
			int end = builder.length();
			
			TagSpan span = new TagSpan(t.getTagName());
			span.setOnTagClickListener(tagOnClickListener);

			builder.setSpan(span, start, end, flags);
		}
    }
}