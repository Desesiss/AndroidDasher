
/*
  This file is part of JDasher.

  JDasher is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  JDasher is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with JDasher; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

  Copyright (C) 2006      Christopher Smowton <cs448@cam.ac.uk>

  JDasher is a port derived from the Dasher project; for information on
  the project see www.dasher.org.uk; for information on JDasher itself
  and related projects see www.smowton.net/chris

*/
package dasher.applet;

import java.awt.Font;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Timer;
import java.util.TimerTask;

import java.awt.datatransfer.Clipboard;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.swing.*;

import dasher.*;

/**
 * <p>Applet containing a JDasherScreen panel, a JDasherEdit TextBox,
 * and a set of menus to set relevant parameters.</p>
 * <p>Also instantiates a {@link JDasher} object, implementing resource-getting
 * methods to attempt to get files over http from the applet codebase directory
 * as well as within the .jar (in the same package as this class) as a fallback.
 * Both of these methods require the existence of a "files.txt" in the relevant
 * location (in the applet codebase directory, or in dasher/applet/ within the .jar).
 */
public class JDasherApplet extends JApplet implements MouseListener, KeyListener, JDasherMenuBarListener, dasher.applet.font.FontListener {

	/** We try to render a new frame every <this number> of milliseconds*/
	private static final int TIME_BETWEEN_FRAMES=20;
	/**
	 * Instance of Dasher which does the work
	 */
	private JDasher Dasher;

	/**
	 * Panel object which will reflect those drawings on the GUI
	 */
	private JDasherPanel panel;
	
	/**
	 * Overlay to display when Dasher is locked
	 */
	private ScreenOverlay ProgressMeter;
	
	/**
	 * Edit box in which typed text appears
	 */
	private JDasherEdit EditBox;
	
	/**
	 * Clipboard object
	 */
	public Clipboard m_Clipboard;
	
	/**
	 * Scheduling agent for all operations that manipulate {@link #Dasher},
	 * including repainting. 
	 */
	private final Timer taskScheduler = new Timer();
	
	/**
	 * Date of last build; appears in About box
	 */
	public final String buildDate = "22:17 08/10/2008";
	
	//try to fill in resourceFiles...
	{
		List<String> res = new ArrayList<String>();
		InputStream in = getClass().getResourceAsStream("files.txt");
		if (in!=null) { 
			BufferedReader rdr = new BufferedReader(new InputStreamReader(in));
			try {
				for (String line; (line=rdr.readLine())!=null;)
					res.add(line);
				rdr.close();
			} catch (IOException e) {
				System.out.println("In reading package resource files.txt: "+e);
			}
		}
		//hope we got something useful out of that!
		resourceFiles = res.toArray(new String[res.size()]);
	}
	/**
	 * Instantiates Dasher, gets a handle to the system clipboard
	 * if possible, calls constructGUIPanel to produce our GUI,
	 * and informs Dasher of the Screen Panel created by this method
	 * using the ChangeScreen method.
	 * <p>
	 * Finally, we call constructMenus to produce our menu bar.
	 */
	public void init() {
		Dasher = new JDasher() {
			private final List<String> webFiles = new ArrayList<String>();
			/**Constructor - look for file list over http...*/
			{
				try {
					java.net.URLConnection conn = new URL(getCodeBase(),"files.txt").openConnection();
					BufferedReader rdr = new BufferedReader(new InputStreamReader(conn.getInputStream()));
					for (String line; (line=rdr.readLine())!=null;)
						webFiles.add(line);
					Collections.sort(webFiles);
				} catch (Exception e) {
					System.out.println("Could not open file list:");
					e.printStackTrace(System.out);
				}
			}
				
			@Override
			protected void CreateModules() {
				super.CreateModules();
				JMouseInput m_MouseInput = new JMouseInput();
				RegisterModule(m_MouseInput);
				JDasherApplet.this.panel.addMouseMotionListener(m_MouseInput);
			}
			//task which will perform at least one more repaint, or null if no such will.
			private TimerTask repaintTask;
			//request to any repaintTask, to perform another repaint;
			// must ensure there _is_ a repaintTask before setting to true.
			private boolean m_bRepaintScheduled;
			@Override public void HandleEvent(EParameters eParam) {
				super.HandleEvent(eParam);
				if (eParam == Ebp_parameters.BP_DASHER_PAUSED) {
					if (!GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED))
						Redraw(true);
				}
			}
			
			/** Called once on the Swing GUI Thread, at startup, when the JDasherPanel
			 * first becomes paintable. After that, only called from taskScheduler thread.
			 */
			@Override public void Redraw(boolean bRedrawNodes) {
				super.Redraw(bRedrawNodes);
				if (repaintTask==null)
					taskScheduler.schedule(repaintTask = new TimerTask() {
						@Override public void run() {
							m_bRepaintScheduled=false;
							panel.swapBuffers();
							if (GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED) && !m_bRepaintScheduled) {
								repaintTask=null;
								cancel();
							}
						}
					}, 0, TIME_BETWEEN_FRAMES);
				m_bRepaintScheduled=true;
			}
			
			@Override public void Message(String msg, int iSeverity) { // Requested message display
				// Convert internal message types to those used by JOptionPane.
				int iType = (iSeverity==0) ? JOptionPane.INFORMATION_MESSAGE
							: (iSeverity == 1) ? JOptionPane.WARNING_MESSAGE
							: (iSeverity == 2) ? JOptionPane.ERROR_MESSAGE : -1;
				JOptionPane.showMessageDialog(JDasherApplet.this, msg, "JDasher", iType);
			}
			
			@Override public void Lock(String msg, int iPercent) {
				if(iPercent>=0) {
					ProgressMeter.setVisible(true);
					ProgressMeter.setProgressBarVisible(true);
					
					try { 
						java.awt.Point myloc = JDasherApplet.this.getLocationOnScreen();
						ProgressMeter.setLocation(((myloc.x + getWidth()) / 2) - 100, ((myloc.y + getHeight()) / 2) - 50);
					}
					catch(Exception e) {
						// ignore; this means we're not visible.
					}
								
					ProgressMeter.setText(msg);
					
					ProgressMeter.setProgress(iPercent, 100);
				}
				else {
					ProgressMeter.setVisible(false);
				}
			}

			/** First looks for a file over http in the same location as our codebase;
			 * <em>if</em> that fails, we fall back to looking for a packaged resource
			 * of the specified name, i.e. baked into the .jar file. Note if we find
			 * a file over http, we do not look in the jar file as well - the assumption
			 * is that both are "system" training texts and alternatives to each other,
			 * rather than supplements, and the files accessible over http supercede
			 * those in the archive.
			 */
		    @Override
			protected void GetStreams(String fname, Collection<InputStream> into) {
		    	try {
		    		//System.out.println("Opening "+new URL(getCodeBase(),fname));
		    		InputStream in = new URL(getCodeBase(),fname).openConnection().getInputStream();
		    		if (in!=null) {
		    			into.add(in);
		    			return;
		    		}
		    	} catch (Exception e) {
		    		System.out.println(e.toString());
		    	}
		    	System.out.println("Could not find "+fname+" over web");
		    	InputStream in = getClass().getResourceAsStream(fname);
				if (in!=null) into.add(in);
			}

		    /** Looks for files whose name begins with the specified prefix and
		     * ends with ".xml".
			 * Due to the difficulty in enumerating the contents of a JAR
			 * file, at present this just uses those filenames hardcoded in
			 * {@link JDasherApplet#fileList}. TODO ideally we should
			 * read the file list from some fixed location on the web (outside
			 * the .jar), and have that autogenerated from a directory listing. 
		     */
			@Override
			protected void ScanXMLFiles(XMLFileParser parser, String prefix) {
				//We load the hard-baked-in files first....
				for (String s : resourceFiles) {
					if (!s.startsWith(prefix) || !s.endsWith(".xml")) continue;
					InputStream in = getClass().getResourceAsStream(s);
					if (in==null) {
						System.out.println("No resource inputstream for "+s);
						continue;
					}
					try {
						parser.ParseFile(in, false);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				//and _then_ look on the web: both AlphIO and ColourIO will replace
				// any existing definitions of alphabets with the same name
				// (and files on the latter location supercede the former)
				int i = Collections.binarySearch(webFiles, prefix);
				if (i<0) i=-(i+1); //not found => start at first index after
				for (String s; i < webFiles.size() && (s=webFiles.get(i)).startsWith(prefix); i++) {
					if (!s.endsWith(".xml")) continue;
					try {
						parser.ParseFile(new URL(getCodeBase(),s).openConnection().getInputStream(), false);
					} catch (Exception e) {
						System.out.println("Error trying to read URLConnection for "+s+": "+e.toString());
					}
				}
			}
			
			@Override
			public void deleteText(String ch, double prob) {
				EditBox.deleteText(ch);
			}

			@Override
			public ListIterator<Character> getContext(int iOffset) {
				return EditBox.getContext(iOffset);
			}

			@Override
			public void outputText(String ch, double prob) {
				EditBox.outputText(ch);
			}
			
		};
		
		/* Try to create a clipboard */
		
		try {
			m_Clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
		}
		catch(Exception e) {
			System.out.printf("Exception retrieving the system clipboard: %s%n", e.toString());
			m_Clipboard = new java.awt.datatransfer.Clipboard("Private clipboard");
		}
		
		ProgressMeter = new ScreenOverlay();
		
		//construct the GUI...
		getContentPane().add(constructGUIPanel(this.getSize()));
		
		//Start training...of course we do this on the Dasher-manipulating
		// background thread (taskqueue)
		final Object realized = new Object();
		taskScheduler.schedule(new TimerTask() {
			@Override public void run() {
				Dasher.Realize();
				synchronized(realized) {
					realized.notifyAll();
				}
			}
		}, 0);
		
		//Don't call ChangeScreen here - it'll get called automatically
		// by the renderer when it first sees the size of the panel.
				
		panel.addMouseListener(this);
		this.addKeyListener(this);
				
		/* The applet itself will handle MouseEvents relating to clicks; these will
		 * be fed into the interface via KeyDown, which also accounts for mouse-clicks.
		 * Further it handles actual keyboard events. The code for this may be moved
		 * out to some dedicated event-handler code in the future, but there is no
		 * particular reason to do so other than for tidiness' sake.
		 */
		//wait for Dasher to finish realizing, before we can make the menu bar
		// (this needs the DasherInterface's module list)
		while (true) {
			synchronized(realized) {
				try {realized.wait(); break;}
				catch (InterruptedException e) {}
			}
		}
		
		/* Next, make our menus */

		JDasherMenuBar MenuBar = new JDasherMenuBar(Dasher, this);
		
		m_Clipboard.addFlavorListener(MenuBar);
		
		this.setJMenuBar(MenuBar);
	}
	
	/**
	 * Sets up a Panel containing the entire Applet's GUI, and
	 * returns the Panel.
	 * <p>
	 * The Panel contains a JDasherScreen (which is stored in the
	 * Screen variable) and a JDasherEdit (stored in EditBox).
	 * 
	 * @param size Size of the panel to be created
	 * @return Created Panel
	 */
	private JPanel constructGUIPanel(java.awt.Dimension size) {

		JPanel GUIPanel = new JPanel();

		GUIPanel.setSize(size);

		GUIPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10,10,10,10));

		GUIPanel.setLayout(new javax.swing.BoxLayout(GUIPanel, javax.swing.BoxLayout.Y_AXIS));

		java.awt.Dimension EditSize = new java.awt.Dimension(GUIPanel.getWidth() - 20, GUIPanel.getHeight() / 10);

		panel = new JDasherPanel(Dasher);

		GUIPanel.add(panel);

		/* The Screen is a specialisation of JPanel. All drawing is done by
		 * its paintComponent method. This is different to the original Dasher,
		 * which painted in a bottom-up method, with Dasher causing its Screen
		 * obejct to me modified; here the Screen object is very much in charge,
		 * and Dasher is invoked to determine its appearance.
		 */

		EditBox = new JDasherEdit(3,80, Dasher);

		EditBox.setLineWrap(true);

		JScrollPane EditScroll = new JScrollPane(EditBox);

		EditScroll.setPreferredSize(EditSize);
		EditScroll.setMaximumSize(EditSize);
		EditScroll.setMinimumSize(EditSize);
		EditScroll.setSize(EditSize);

		/* The EditBox is a specialisation of a JTextPane, but a much lighter
		 * one than the Screen. */

		GUIPanel.add(EditScroll);

		return GUIPanel;

	}
	
	/** List of alphabet/colour files hard-baked into the .jar.
	 * Read in from files.txt (itself a resource in the .jar) by constructor.
	 * We use these as a fallback if http retrieval fails.
	 */
	private final String[] resourceFiles;
	
	/**
	 * Ignored. MousePressed and Released are handled separately.
	 */
	public void mouseClicked(MouseEvent e) {
	}

	/**
	 * Ignored
	 */
	public void mouseEntered(MouseEvent arg0) {
	}

	/**
	 * Ignored
	 */
	public void mouseExited(MouseEvent arg0) {
	}
	
	public void mousePressed(MouseEvent arg0) {
		Dasher.KeyDown(System.currentTimeMillis(), 100);
	}

	public void mouseReleased(MouseEvent arg0) {
		Dasher.KeyUp(System.currentTimeMillis(), 100);
	}
	
	/**
	 * We respond to the following key presses:
	 * <p>
	 * CTRL: Set speed boost factor to 175<br>
	 * SHIFT: Set speed boost factor to 25.
	 * </p>
	 */	
	public void keyPressed(KeyEvent e) {
		boost: {
			final int newBoostFactor;
			if(e.getKeyCode() == KeyEvent.VK_CONTROL) 
				newBoostFactor = 175; // Speed boost for pressing CTRL. Should this be in the interface?
			else if(e.getKeyCode() == KeyEvent.VK_SHIFT)
				newBoostFactor = 25; // Speed reduced when SHIFT pressed. As above?
			else break boost;
			menuSetLong(Elp_parameters.LP_BOOSTFACTOR, newBoostFactor);
		}
	}
	
	/**
	 * Upon releasing the space bar, we signal Dasher a KeyDown
	 * event with a key of zero.
	 * <p>
	 * If either CTRL or SHIFT are released, the speed boost
	 * constant is reset to 100, 175 or 25, dependent on which
	 * keys are still down.
	 */
	public void keyReleased(KeyEvent e) {
		
		/* Dasher will start when SPACE is pressed. */
		
		if(e.getKeyCode() == KeyEvent.VK_SPACE) {
			Dasher.KeyDown(System.currentTimeMillis(), 0);
		}
		
		
		/* This completes the boost-key implementation by considering
		 * whether the other boost key is currently pressed when one
		 * is released.
		 */
		boost: {
			final int newBoostFactor;
			if(e.getKeyCode() == KeyEvent.VK_CONTROL) {
				newBoostFactor = (e.isShiftDown()) ? 25 : 100;
			} else if(e.getKeyCode() == KeyEvent.VK_SHIFT) {
				newBoostFactor = (e.isControlDown()) ? 175 : 100;
			} else break boost;
			menuSetLong(Elp_parameters.LP_BOOSTFACTOR, newBoostFactor);
		}
	}

	/**
	 * Ignored
	 */
	public void keyTyped(KeyEvent arg0) {
	}

	/**
	 * Cancels our new frame scheduler, and calls Dasher's
	 * DestroyInterface method to give it an opportunity to
	 * clean up if necessary.
	 * <p>
	 * Ultimately any neglected cleaning is likely not to
	 * cause a problem, as we are about to stop the application.
	 */
	public void stop() {
		taskScheduler.cancel();
		Dasher.DestroyInterface();
	}
	
	/**
	 * Sets our edit box font
	 * 
	 * @param f New font
	 */
	public void setNewFont(Font f) {
		
		EditBox.setFont(f);
		
	}

	/**
	 * Copies the current edit box selection to the clipboard if
	 * possible.
	 */
	public void menuCopy() {
		try {
			m_Clipboard.setContents(new java.awt.datatransfer.StringSelection(EditBox.getSelectedText()), null);
		}
		catch (Exception ex) {
			System.out.printf("Copy to clipboard failed: %s%n", ex.toString());
		}
	}
	
	/**
	 * Cuts the current edit box selection to the clipboard if
	 * possible.
	 */
	public void menuCut() {
		try {
			m_Clipboard.setContents(new java.awt.datatransfer.StringSelection(EditBox.getSelectedText()), null);
			EditBox.replaceSelection("");
		}
		catch (Exception ex) {
			System.out.printf("Cut to clipboard failed: %s%n", ex.toString());
		}
	}

	/**
	 * Quits Dasher
	 */
	public void menuExit() {
		System.exit(0); // Should make this tidier...
	}

	/**
	 * Displays an About dialog showing the compilation date.
	 */
	public void menuHelpAbout() {
		
		JOptionPane.showMessageDialog(this, "JDasher\nVersion compiled: " + buildDate);
		
	}

	/**
	 * Blanks the EditBox and invalidates our current context.
	 */
	public void menuNew() {
		this.EditBox.setText("");
		//??? done by EditBox? this.Dasher.SetOffset(0,true); //InvalidateContext(true);
	}

	/**
	 * Attempts to paste from the clipboard, overwriting our current
	 * EditBox selection if there is one.
	 */
	public void menuPaste() {
		try {
			java.awt.datatransfer.Transferable clipContents = m_Clipboard.getContents(null);
			String temp = (String)clipContents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
			EditBox.replaceSelection(temp);
		}
		catch (Exception ex) {
			System.out.printf("Paste from clipboard failed: %s%n", ex.toString());
		}
	}

	/**
	 * Opens the Select Font dialog in order to choose a new
	 * EditBox font
	 */
	public void menuSelFont() {
		new dasher.applet.font.JFontDialog(this, EditBox.getFont());
	}
	
	/** Called by menubar to set a string parameter - we do so
	 * the {@link #worker} thread. */
	public void menuSetString(final Esp_parameters param, final String val) {
		taskScheduler.schedule(new TimerTask() {
			public void run() {
				Dasher.SetStringParameter(param, val);
			}
		},0);
	}
	
	/** Called by menubar to set a long parameter - we do so
	 * the {@link #worker} thread. */
	public void menuSetLong(final Elp_parameters param, final long val) {
		taskScheduler.schedule(new TimerTask() {
			public void run() {
				Dasher.SetLongParameter(param, val);
			}
		},0);
	}
	
	/** Called by menubar to set a bool parameter - we do so
	 * the {@link #worker} thread. */
	public void menuSetBool(final Ebp_parameters param, final boolean val) {
		taskScheduler.schedule(new TimerTask() {
			public void run() {
				Dasher.SetBoolParameter(param, val);
			}
		},0);
	}
	
	/**
	 * Checks with the clipboard whether a given data flavour
	 * is enabled.
	 * <p>
	 * Typically this is used to check whether it is sensible
	 * to attempt Paste at the moment.
	 * 
	 * @param flavour Flavour to check availability
	 * @return True if available at present, false otherwise. 
	 */
	public boolean isDataFlavorAvailable(java.awt.datatransfer.DataFlavor flavour) {
		return m_Clipboard.isDataFlavorAvailable(flavour);
	}
	
	/**
	 * Retrieves the current EditBox text. This method exists
	 * for the purposes of JavaScript calling in to invoke unsafe
	 * functions.
	 * <p>
	 * Thankfully, this doesn't work on FireFox.
	 * 
	 * @return Current EditBox contents
	 */
	public String getCurrentEditBoxText() {
		
		return EditBox.getText();
		
	}
}
