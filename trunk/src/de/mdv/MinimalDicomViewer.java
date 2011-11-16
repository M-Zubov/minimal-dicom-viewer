/*
 * copyright (C) 2011 Robert Schmidt
 *
 * This file <MinimalDicomViewer.java> is part of Minimal Dicom Viewer.
 *
 * Minimal Dicom Viewer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Minimal Dicom Viewer is distributed as Open Source Software ( OSS )
 * and comes WITHOUT ANY WARRANTY and even with no IMPLIED WARRANTIES OF MERCHANTABILITY,
 * OF SATISFACTORY QUALITY, AND OF FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License ( GPLv3 ) for more details.
 *
 *
 * You should have received a copy of the GNU General Public License
 * along with Minimal Dicom Viewer. If not, see <http://www.gnu.org/licenses/>.
 *
 * Released date: 13-11-2011
 *
 * Version: 1.0
 * 
 */
package de.mdv;

import java.io.File;
import java.util.Arrays;

import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.VRMap;
import org.dcm4che2.io.DicomInputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

public class MinimalDicomViewer extends Activity implements SeekBar.OnSeekBarChangeListener
{

	public static final String FILE_NAME 			= "file_name";
	public static final String SEEKBAR_VISIBILITY 	= "SeekBar_Visibility";
	public static final String DISCLAIMER_ACCEPTED 	= "Disclaimer_Accepted";
	
	
	public static final short OUT_OF_MEMORY = 0;
	
	/**
	 * The thread is started.
	 */
	public static final short STARTED = 1;
	
	/**
	 * The thread is finished.
	 */
	public static final short FINISHED = 2;
	
	/**
	 * The thread progression update.
	 */
	public static final short PROGRESSION_UPDATE = 3;
	
	/**
	 * An error occurred while the thread running that cannot
	 * be managed.
	 */
	public static final short UNCATCHABLE_ERROR_OCCURRED = 4;
	
	
	
	
	private DicomImageView imageView;
	private DicomFileLoader dicomFileLoader;
	private File[] fileArray = null;
	private int currentFileIndex = -1;
	private String actualFileName = "";
	private boolean paintInverted = false;
	
	private boolean isInitialized = false;
	
	
	private static final short MENU_CONFIGURE_LANGUAGE = 0;
	private static final short MENU_CONFIGURE_DISCLAIMER_DIALOG = 1;
	private static final short MENU_SWITCH_SEEKBAR_VISIBILITY = 2;
	private static final short MENU_INVERT = 3;
	private static final short MENU_ABOUT = 4;
	
	
	private static final short PROGRESS_IMAGE_LOAD = 0;
	private ProgressDialog imageLoadingDialog;
	
	private SeekBar brightnessSeekBar;
	private TextView brightnessValue;
	private TextView brightnessLabel;
	
	private boolean allowEvaluateProgressValue = true;
	private boolean seekBarVisibility = true;
	
	public static final String PREFERENCES_NAME = "MDVPreferencesFile";
	
	
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        VRMap.getVRMap();
        VRMap.loadVRMap( "org/dcm4che2/data/VRMap.ser" );
        setContentView(R.layout.main);
        imageView = (DicomImageView)findViewById(R.id.imageView);
        brightnessSeekBar = (SeekBar)findViewById(R.id.brightnessSeekBar);
        brightnessValue = (TextView)findViewById(R.id.brightnessValue);
        brightnessLabel = (TextView)findViewById(R.id.brightnessLabel);
        brightnessLabel.setText(Messages.getLabel(Messages.LABEL_BRIGHTNESS, Messages.Language));
        
        // Set the seek bar change index listener
        brightnessSeekBar.setOnSeekBarChangeListener(this);
        brightnessSeekBar.setMax(255);
        
        String fileName = null;        
        
        SharedPreferences settings = getSharedPreferences(PREFERENCES_NAME, 0);
        if(settings != null)
        {
        	boolean value = settings.getBoolean(SEEKBAR_VISIBILITY, true);
        	if(!value)
        	{
        		brightnessSeekBar.setVisibility(View.INVISIBLE);
				brightnessLabel.setVisibility(View.INVISIBLE);
				brightnessValue.setVisibility(View.INVISIBLE);
				seekBarVisibility = false;
        	}
        }
		
		// If the saved instance state is not null get the file name
		if (savedInstanceState != null) 
		{
			fileName = savedInstanceState.getString(FILE_NAME);
		} 
		else // Get the intent
		{
			Intent intent = getIntent();
			if (intent != null) 
			{
				Bundle extras = intent.getExtras();
				fileName = extras == null ? null : extras.getString("DicomFileName");
			}
		}
		if (fileName == null) 
		{
			showExitAlertDialog(Messages.getLabel(Messages.ERROR_LOADING_FILE, Messages.Language),
					Messages.getLabel(Messages.THE_FILE_CANNOT_BE_LOADED, Messages.Language)+"\n\n" +
					Messages.getLabel(Messages.CANNOT_RETRIEVE_NAME, Messages.Language));
		} 
		else 
		{
			// Get the File object for the current file
			File currentFile = new File(fileName);
			
			// Start the loading thread to load the DICOM image
			actualFileName = fileName;
			dicomFileLoader = new DicomFileLoader(loadingHandler, fileName);
			dicomFileLoader.start();
			//busy = true;
			
			
			// Get the files array = get the files contained
			// in the parent of the current file
			fileArray = currentFile.getParentFile().listFiles(new DicomFileFilter());
			
			// Sort the files array
			Arrays.sort(fileArray);
			
			// If the files array is null or its length is less than 1,
			// there is an error because it must at least contain 1 file:
			// the current file
			if (fileArray == null || fileArray.length < 1) 
			{
				showExitAlertDialog(Messages.getLabel(Messages.ERROR_LOADING_FILE, Messages.Language),
						Messages.getLabel(Messages.THE_FILE_CANNOT_BE_LOADED, Messages.Language)+"\n\n" +
						Messages.getLabel(Messages.NO_DICOM_FILES_IN_DIRECTORY, Messages.Language));
			} 
			else 
			{
				// Get the file index in the array
				currentFileIndex = getIndex(currentFile);
				
				// If the current file index is negative
				// or greater or equal to the files array
				// length there is an error
				if (currentFileIndex < 0 || currentFileIndex >= fileArray.length) 
				{
					showExitAlertDialog(Messages.getLabel(Messages.ERROR_LOADING_FILE, Messages.Language),
							Messages.getLabel(Messages.THE_FILE_CANNOT_BE_LOADED, Messages.Language)+"\n\n" +
							Messages.getLabel(Messages.FILE_IS_NOT_IN_DIRECTORY, Messages.Language));
				// Else initialize views and navigation bar
				} 
			}
		}
    }
    
    
    
    private void setFilenameLabel(TextView textView, String text)
    {
    	String toPrint = text.substring(text.lastIndexOf("/") + 1);
    	textView.setTextColor(Color.rgb(255, 0, 0));
    	textView.setText(Messages.getLabel(Messages.FILE, Messages.Language) +": " + toPrint);
    }
    
    
    
    @Override
	protected void onResume() 
    {
		// If there is no external storage available, quit the application
		if (!ExternalDevice.isExternalDeviceAvailable()) 
		{
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(Messages.getMessageExternalDevice(Messages.Language))
				   .setTitle(Messages.getMessageExternalDeviceHeader(Messages.Language))
			       .setCancelable(false)
			       .setPositiveButton("Exit", new DialogInterface.OnClickListener() 
			       {
			           public void onClick(DialogInterface dialog, int id) 
			           {
			                MinimalDicomViewer.this.finish();
			           }
			       });
			AlertDialog alertDialog = builder.create();
			alertDialog.show();
		}
		super.onResume();
	}
    
    
    
    @Override
	protected void onPause() 
    {
		// We wait until the end of the loading thread
		// before putting the activity in pause mode
		if (dicomFileLoader != null) 
		{
			// Wait until the loading thread die
			while (dicomFileLoader.isAlive()) 
			{
				try 
				{
					synchronized(this) 
					{
						wait(10);
					}
				} 
				catch (InterruptedException e){}
			}
		}
		super.onPause();
		storePreferencesData();
	}
    
    
    
    @Override
	protected void onStop() 
    {
    	super.onStop();
    	storePreferencesData();
    }
    
    
    @Override
	protected void onDestroy() 
    {
		super.onDestroy();
		fileArray = null;
		dicomFileLoader = null;
		
		// Free the drawable callback
		if (imageView != null) 
		{
			Drawable drawable = imageView.getDrawable();
			if (drawable != null)drawable.setCallback(null);
		}
	}
    
    @Override
	protected void onSaveInstanceState(Bundle outState) 
    {
		super.onSaveInstanceState(outState);
		// Save the current file name
		String currentFileName = fileArray[currentFileIndex].getAbsolutePath();
		outState.putString(FILE_NAME, currentFileName);
	}
    
    
    
    @Override
	protected Dialog onCreateDialog(int id) 
    {
		switch(id) 
		{
         // Create image load dialog
        case PROGRESS_IMAGE_LOAD:
        	imageLoadingDialog = new ProgressDialog(this);
        	imageLoadingDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        	imageLoadingDialog.setMessage(Messages.getLabel(Messages.LOADING_IMAGE, Messages.Language));
        	imageLoadingDialog.setCancelable(false);
        	return imageLoadingDialog;
            
        default:
            return null;
        }
    }
    
    
    @Override
	public void onLowMemory() 
    {
		// Hint the garbage collector
		System.gc();
		// Show the exit alert dialog
		showExitAlertDialog(Messages.getLowMemory(Messages.Language), Messages.getLowMemory(Messages.Language));
		super.onLowMemory();
	}
    
    
    @Override
	public boolean onCreateOptionsMenu(Menu menu) 
    {
    	super.onCreateOptionsMenu(menu);
    	menu.add(0, MENU_INVERT, MENU_INVERT, Messages.getLabel(Messages.MENU_INVERT_PICTURE, Messages.Language));
		menu.add(1, MENU_ABOUT, MENU_ABOUT, Messages.getLabel(Messages.MENU_ABOUT, Messages.Language));
		menu.add(2, MENU_SWITCH_SEEKBAR_VISIBILITY, MENU_SWITCH_SEEKBAR_VISIBILITY, Messages.getLabel(Messages.MENU_CHANGE_VISIBLITY, Messages.Language));
		menu.add(3, MENU_CONFIGURE_LANGUAGE, MENU_CONFIGURE_LANGUAGE, Messages.getLabel(Messages.CONFIGURE_LANGUAGE, Messages.Language));
		menu.add(4, MENU_CONFIGURE_DISCLAIMER_DIALOG, MENU_CONFIGURE_DISCLAIMER_DIALOG, Messages.getLabel(Messages.CONFIGURE_DISCLAIMER_DIALOG_MULTILINE, Messages.Language));
		return true;
    }
    
    
    @Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) 
	{
		switch (item.getItemId()) 
		{
		case MENU_ABOUT:
			Dialog dialog = new Dialog(this);
        	dialog.setContentView(R.layout.dialog_about);
        	dialog.setTitle(Messages.getLabel(Messages.ABOUT_HEADER, Messages.Language));
        	TextView text = (TextView)dialog.findViewById(R.id.AboutText);
        	text.setText(Messages.getAboutMessage(Messages.Language));
        	dialog.show();
			return true;
			
		case MENU_INVERT:
			paintInvert();
			imageView.updateMatrix();
			return true;
			
		case MENU_SWITCH_SEEKBAR_VISIBILITY:
			int visibility = brightnessSeekBar.getVisibility();
			if(visibility == View.VISIBLE)
			{
				visibility = View.INVISIBLE;
				seekBarVisibility = false;
			}
			else
			{
				visibility = View.VISIBLE;
				seekBarVisibility = true;
			}
			brightnessSeekBar.setVisibility(visibility);
			brightnessLabel.setVisibility(visibility);
			brightnessValue.setVisibility(visibility);
			storePreferencesData();
			return true;
			
		case MENU_CONFIGURE_DISCLAIMER_DIALOG:
			displayConfigureDisclaimer();
			return true;
			
		case MENU_CONFIGURE_LANGUAGE:
			displayLanguage();
			return true;
			
		default:
			return super.onMenuItemSelected(featureId, item);
		}
	}
    
    
    /**
     * store the preferences
     */
    private void storePreferencesData()
    {
    	SharedPreferences settings = getSharedPreferences(PREFERENCES_NAME, 0);
	    SharedPreferences.Editor editor = settings.edit();	    
	    if(seekBarVisibility)editor.putBoolean(SEEKBAR_VISIBILITY, true );
		else editor.putBoolean(SEEKBAR_VISIBILITY, false );
	    editor.commit();
    }
    
    
    private void paintInvert()
    {
    	allowEvaluateProgressValue = false;
		brightnessSeekBar.setProgress(0);
		ImageGray16Bit imageGray16Bit = imageView.getImage();
		int imageData[] = imageGray16Bit.getOriginalImageData();
		imageData = DicomHelper.invertPixels(imageData);
		imageGray16Bit.setImageData(imageData);
		imageGray16Bit.setOriginalImageData(imageData);
		imageView.setImage(imageGray16Bit);
		imageView.draw();
		imageView.paintCachedSize();
		allowEvaluateProgressValue = true;
		if(!paintInverted)paintInverted = true;
		else paintInverted = false;
    }
    
    
    /* (non-Javadoc)
	 * @see android.widget.SeekBar.OnSeekBarChangeListener#onProgressChanged(android.widget.SeekBar, int, boolean)
	 */
	public synchronized void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) 
	{
		
		brightnessValue.setText("" + progress);
		if(allowEvaluateProgressValue)
		{
			ImageGray16Bit imageGray16Bit = imageView.getImage();
			int imageData[] = imageGray16Bit.getOriginalImageData();
			imageData = DicomHelper.setBrightness(imageData, progress);
			imageGray16Bit.setImageData(imageData);
			imageView.setImage(imageGray16Bit);
			imageView.draw();
		}
	}
	

	// Needed to implement the SeekBar.OnSeekBarChangeListener
	public void onStartTrackingTouch(SeekBar seekBar) 
	{
		// nothing to do.
	}

	
	// Needed to implement the SeekBar.OnSeekBarChangeListener
	public void onStopTrackingTouch(SeekBar seekBar) 
	{
		System.gc(); // TODO needed ?
	}
	
	
	/**
	 * Get the index of the file in the files array.
	 * @param file
	 * @return Index of the file in the files array
	 * or -1 if the files is not in the list.
	 */
	private int getIndex(File file) {
		
		if (fileArray == null) 
			throw new NullPointerException("The files array is null.");
		
		for (int i = 0; i < fileArray.length; i++) 
		{
			if (fileArray[i].getName().equals(file.getName()))return i;
		}
		return -1;
	}
	
	
	private void setImage(ImageGray16Bit image) 
	{
		if (image == null)
			throw new NullPointerException("The 16-Bit grayscale image is null");
		
		try 
		{
			// Set the image
			imageView.setImage(image);
			
			// If it is not initialized, set the window width and center
			// as the value set in the LISA 16-Bit grayscale image
			// that comes from the DICOM image file.
			if (!isInitialized) 
			{
				isInitialized = true;
			} 
			imageView.draw();
		} 
		catch (OutOfMemoryError ex) 
		{
			System.gc();
			showExitAlertDialog(Messages.getHeaderOutOfMemoryErrorMessage(Messages.Language),
					Messages.getOutOfMemoryErrorMessage(Messages.Language));
		} 
		catch (ArrayIndexOutOfBoundsException ex) 
		{
			showExitAlertDialog(Messages.getHeaderIndexOutOfBoundsMessage(Messages.Language),
					Messages.getIndexOutOfBoundsMessage(Messages.Language));
		}
	}
	
	
	
	private void showExitAlertDialog(String title, String message) 
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(message)
			   .setTitle(title)
		       .setCancelable(false)
		       .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		               MinimalDicomViewer.this.finish();
		           }
		       });
		AlertDialog alertDialog = builder.create();
		alertDialog.show();
	}
	
	
	
	
	private final Handler loadingHandler = new Handler() 
	{
		public void handleMessage(Message message) 
		{
			switch (message.what) 
			{

			case STARTED:
				showDialog(PROGRESS_IMAGE_LOAD);
				break;

			case FINISHED:
				try 
				{
					dismissDialog(PROGRESS_IMAGE_LOAD);
				} 
				catch (IllegalArgumentException ex) 
				{	
					// Do nothing		
				}
				if (message.obj instanceof ImageGray16Bit) 
				{
					setImage((ImageGray16Bit) message.obj);
				}
				setFilenameLabel((TextView)findViewById(R.id.currentFileLabel), actualFileName);

				break;

			case UNCATCHABLE_ERROR_OCCURRED:
				try 
				{
					dismissDialog(PROGRESS_IMAGE_LOAD);
				} 
				catch (IllegalArgumentException ex){}

				// Get the error message
				String errorMessage;

				if (message.obj instanceof String)
					errorMessage = (String) message.obj;
				else
					errorMessage = "Unknown error";

				// Show an alert dialog
				showExitAlertDialog("[ERROR] Loading file",
						"An error occured during the file loading.\n\n"
						+ errorMessage);
				break;

			case OUT_OF_MEMORY:
				try 
				{
					dismissDialog(PROGRESS_IMAGE_LOAD);
				} 
				catch (IllegalArgumentException ex){}

				// Show an alert dialog
				showExitAlertDialog(Messages.getHeaderOutOfMemoryErrorMessage(Messages.Language),
						Messages.getOutOfMemoryErrorMessage(Messages.Language));
				break;
			}
		}
	};
    
    
	private static final class DicomFileLoader extends Thread 
	{
		// The handler to send message to the parent thread
		private final Handler mHandler;
		
		// The file to load
		private final String fileName;
		
		public DicomFileLoader(Handler handler, String fileName) 
		{
			
			if (handler == null)
				throw new NullPointerException("The handler is null while calling the loading thread.");
			
			mHandler = handler;
			
			if (fileName == null)
				throw new NullPointerException("The file is null while calling the loading thread.");
			
			this.fileName = fileName;
		}
		
		public void run() 
		{
			// If the image data is null, do nothing.
			File f = new File(fileName);
			if (!f.exists()) 
			{
				Message message = mHandler.obtainMessage();
				message.what = UNCATCHABLE_ERROR_OCCURRED;
				message.obj = "The file doesn't exist.";
				mHandler.sendMessage(message);
				return;
			}
			
			mHandler.sendEmptyMessage(STARTED);
			// If image exists show image
			try 
			{
				ImageGray16Bit image = null;
				
				BasicDicomObject bdo = new BasicDicomObject();
		    	DicomInputStream dis = new DicomInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(fileName)));
		    	dis.readDicomObject(bdo, -1);
		    	int height = bdo.getInt(org.dcm4che2.data.Tag.Rows);
		    	int width = bdo.getInt(org.dcm4che2.data.Tag.Columns);
		    	int bitsAllocated = bdo.getInt(org.dcm4che2.data.Tag.BitsAllocated);
		    	if(bitsAllocated == 8 || bitsAllocated == 12 || bitsAllocated == 16)
		    	{
		    		byte bytePixels[] = DicomHelper.readPixelData(bdo);
		    		int pixelData[] = DicomHelper.convertToIntPixelData(bytePixels, bitsAllocated, width, height);
		    		image = new ImageGray16Bit();
		    		image.setImageData(pixelData);
		    		image.setOriginalImageData(pixelData);
		    		image.setWidth(width);
		    		image.setHeight(height);
		    	}
				Message message = mHandler.obtainMessage();
				message.what = FINISHED;
				message.obj = image;
				mHandler.sendMessage(message);
				return;
				
			} 
			catch (Exception ex) 
			{
				mHandler.sendEmptyMessage(FINISHED);
			}
		}
	}
    
    
    public synchronized void prevImage(View view) 
    {
		while (dicomFileLoader.isAlive()) 
		{
			try 
			{
				synchronized(this){wait(10);}
			} 
			catch (InterruptedException e) {}
		}
		
		// If the current file index is 0, there is
		// no previous file in the files array
		// We add the less or equal to zero because it is
		// safer
		if (currentFileIndex <= 0) 
		{
			currentFileIndex = 0;
			return;			
		}
		//  Decrease the file index
		currentFileIndex--;
		
		actualFileName = fileArray[currentFileIndex].getAbsolutePath();
		dicomFileLoader = new DicomFileLoader(loadingHandler,fileArray[currentFileIndex].getAbsolutePath());
		
		dicomFileLoader.start();
	}
    
    
    public synchronized void nextImage(View view) 
    {
		// Wait until the loading thread die
		while (dicomFileLoader.isAlive()) 
		{
			try 
			{
				synchronized(this){wait(10);}
			} 
			catch (InterruptedException e) {}
		}
		
		// If the current file index is the last file index,
		// there is no next file in the files array
		// We add the greater or equal to (mFileArray.length - 1)
		// because it is safer
		if (currentFileIndex >= (fileArray.length - 1)) 
		{
			currentFileIndex = (fileArray.length - 1);
			return;
		}
		//  Increase the file index
		currentFileIndex++;
		// Start the loading thread to load the DICOM image
		actualFileName = fileArray[currentFileIndex].getAbsolutePath();
		dicomFileLoader = new DicomFileLoader(loadingHandler,  fileArray[currentFileIndex].getAbsolutePath());
		
		dicomFileLoader.start();
	}
    
    
    public void displayLanguage() 
	{
		String title = "no Title configured";
		title = Messages.getLabel(Messages.CONFIGURE_LANGUAGE, Messages.Language);
		final String items[] = {"English","Deutsch"};
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(title);
		builder.setSingleChoiceItems(items, Messages.Language,new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				// onClick Action
				Messages.Language = whichButton;
			}
		})
		.setPositiveButton(Messages.getLabel(Messages.LABEL_OK, Messages.Language), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				// on Ok button action
				SharedPreferences settings = getSharedPreferences(MinimalDicomViewer.PREFERENCES_NAME, 0);
			    SharedPreferences.Editor editor = settings.edit();	    
			    editor.putInt(MDVFileChooser.SELECTED_LANGUAGE, Messages.Language );
			    editor.commit();
			}
		});
		AlertDialog alertDialog = builder.create();
		alertDialog.show();
	}
    
    
    
    public void displayConfigureDisclaimer() 
	{
		String title = "no Title configured";
		title = Messages.getLabel(Messages.CONFIGURE_DISCLAIMER_DIALOG, Messages.Language);
		int preSelected = MDVFileChooser.bHideDisclaimerDialog == false ? 0 : 1;
		final String items[] = {Messages.getLabel(Messages.SHOW_DISCLAIMER_DIALOG, Messages.Language),Messages.getLabel(Messages.HIDE_DISCLAIMER_DIALOG, Messages.Language)};
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(title);
		builder.setSingleChoiceItems(items, preSelected,new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				// onClick Action
				if(whichButton == 0)
				{
					MDVFileChooser.bHideDisclaimerDialog = false;
				}
				else MDVFileChooser.bHideDisclaimerDialog = true;
				
			}
		})
		.setPositiveButton(Messages.getLabel(Messages.LABEL_OK, Messages.Language), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				// on Ok button action
				SharedPreferences settings = getSharedPreferences(MinimalDicomViewer.PREFERENCES_NAME, 0);
			    SharedPreferences.Editor editor = settings.edit();	    
			    editor.putBoolean(MDVFileChooser.HIDE_DISCLAIMER_DIALOG, MDVFileChooser.bHideDisclaimerDialog );
			    editor.commit();
			}
		});
		AlertDialog alertDialog = builder.create();
		alertDialog.show();
	}
}