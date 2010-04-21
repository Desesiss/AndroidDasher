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

package dasher;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Collection;
import java.util.ListIterator;
import java.io.*;

/**
 * DasherInterfaceBase is the core of Dasher, and the entry
 * point for all high-level functions which control the system
 * as a whole.
 * <p>
 * The majority of the actual work involved in running Dasher is
 * done by the Interface's two main children, a DasherModel and
 * a DasherView; the interface primarily acts as a co-ordinator.
 * <p>
 * The DasherModel represents the tree of DasherNodes which forms
 * the core of Dasher. It knows how to update the model in response
 * to user input, and how to build, destroy and rebuild the tree.
 * <p>
 * The DasherView on the other hand is responsible for taking the abstract
 * information in the Model and rendering it, visually or otherwise,
 * in some way.
 * <p>
 * the Interface simply sits in the middle and co-ordinates
 * whole-system actions such as updating the model and drawing a new frame.
 * <p>
 * The interface has a number of methods which remain abstract;
 * this means that in order to implement Dasher, this class must be
 * extended and the missing methods implemented.
 * <p>
 * Typically one would also extend DasherInput to provide a co-ordinate
 * input of some sort, and implement DasherScreen to provide visual 
 * (or other) display.   
 */
abstract public class CDasherInterfaceBase extends CEventHandler {
	
		
	public CFileLogger g_Logger; // CSFS: No logging yet.
	// public final eLogLevel g_iLogLevel = logNORMAL; // FIXME enums
	// public final int       g_iLogOptions = logTimeStamp | logDateStamp;
	
	/**
	 * Current colour scheme
	 */
	protected CCustomColours m_Colours;
	
	/**
	 * Current DasherModel
	 */
	protected CDasherModel m_DasherModel;
	
	/**
	 * Current DasherScreen
	 */
	protected CDasherScreen m_DasherScreen;
	/**
	 * The screen (i.e. same as {@link #m_DasherScreen}), <em>iff</em> it's an instance of the {@link CMarkerScreen} subinterface;
	 * otherwise, <code>null</code>. (Both set in {@link #ChangeScreen(CDasherScreen)}.
	 */
	protected CMarkerScreen m_MarkerScreen;
	
	/**
	 * Current DasherView
	 */
	protected CDasherView m_DasherView;
	
	/**
	 * Current Input device
	 */
	protected CDasherInput m_Input;
	
	/**
	 * Current AlphIO
	 */
	protected CAlphIO m_AlphIO;
	
	/**
	 * Current ColourIO
	 */
	protected CColourIO m_ColourIO;
	
	/**
	 * Entered text which has not yet been written out to disk
	 */
	protected StringBuffer strTrainfileBuffer;
		
	/**
	 * Our settings repository
	 */
	protected final CSettingsStore m_SettingsStore;
	
	/**
	 * Our logging module
	 */
	protected CUserLog m_UserLog;
	
	/**
	 * Current input filter
	 */
	protected CInputFilter m_InputFilter;
	
	/**
	 * The module manager
	 */
	protected CModuleManager m_oModuleManager;
	
	/**
	 * Lock engaged when Dasher should not respond to
	 * commands for unspecified reason
	 */
	protected boolean m_bGlobalLock; // The big lock
	
	/**
	 * Lock engaged when Dasher is being destroyed
	 */
	protected boolean m_bShutdownLock;
	
	/**
	 * Lock engaged when we're in the process of connecting
	 * to a remote language model
	 */
	protected boolean m_bConnectLock; // Connecting to server.
	
	/**
	 * Somehow determines which alphabet XML files to parse.
	 * 
	 * @param mAlphIO List to fill with filenames to process.
	 */
	public abstract void ScanAlphabetFiles(CAlphIO mAlphIO);
	
	/**
	 * Somehow determine which colour XML files to parse
	 * 
	 * @param mColourIO CColourIO to which all files should be passed
	 */
	public abstract void ScanColourFiles(CColourIO mColourIO);
	
	/**
	 * Should setup the system and user locations; called
	 * early in the startup sequence.
	 * <p>
	 * These correspond to the string parameters SP_SYSTEM_LOC
	 * and SP_USER_LOC respectively.
	 */
	public abstract void SetupPaths();
	
	/**
	 * Called at construction time to make the settings store.
	 * Subclasses should override to return something appropriate
	 * - i.e. to store persistent settings - or return a CSettingsStore
	 * as a fallback.
	 * @param handler EventHandler for the settings store to use (to propagate
	 * parameter change notifications); actually <code>this</code>, i.e. a
	 * CDasherInterfaceBase, but of course the CDasherInterfaceBase bits aren't
	 * initialised yet (whereas the CEventHandler bits are!)
	 * @return
	 */
	protected abstract CSettingsStore createSettingsStore(CEventHandler handler);
	
	/**
	 * Returns an iterator over all characters that have been entered (i.e. up to
	 * the node under the crosshair, inclusive). Initial position of the iterator
	 * should be at the end, i.e. after the most recent character, such that we
	 * can navigate *backwards* using previous()/hasPrevious().
	 * @return a ListIterator of all characters entered
	 */
	public abstract ListIterator<Character> charactersEntered();
	
	/**
	 * Sole constructor. Creates an EventHandler, sets up an
	 * initial context, and creates a ModuleManager. This does
	 * only enough that we can retrieve the event handler for the
	 * purposes of creating further components; as neither the
	 * settings store nor any of Dasher's internal components
	 * yet exist, it is not yet capable of any meaningful task.
	 * <p>
	 * Realize() must be called after construction and before
	 * any other functions which depend upon anything other than
	 * the event handler and module manager.
	 */
	public CDasherInterfaceBase() {
		m_SettingsStore = createSettingsStore(this);
		strTrainfileBuffer = new StringBuffer();
		m_oModuleManager = new CModuleManager();
				
		// Global logging object we can use from anywhere
		// g_logger = new CFileLogger("dasher.log", g_iLogLevel, g_iLogOptions);
		
	}
	
	/**
	 * Realize does the bulk of the work of setting up a working
	 * Dasher. The sequence of actions is as follows:
	 * <p>
	 * <ul>
	 * <li>Sets up the system and user locations (by calling SetupPaths())
	 * <li>Reads in the available alphabets and colour schemes
	 * using ScanAlphabetFiles and then creating a CAlphIO based upon
	 * the filenames returned (and the equivalent for ColourIO).
	 * The resulting objects are stored in m_AlphIO and m_ColourIO.
	 * <li>Calls ChangeColours() and ChangeAlphabet(), which will
	 * create a number of internal components of their own
	 * (see these functions' documentation for details)
	 * <li>Calls CreateFactories(), CreateInput() and CreateInputFilter() to complete
	 * the input setup.
	 * </ul>
	 * <p>
	 * When realize terminates, Dasher will be in a broadly usable
	 * state, tho it will need a screen which should be created
	 * externally and registered with ChangeScreen().
	 */
	public void Realize() {
		
		SetupPaths();
		
		m_AlphIO = new CAlphIO(this);
		ScanAlphabetFiles(m_AlphIO);
		
		m_ColourIO = new CColourIO(this);
		ScanColourFiles(m_ColourIO);
		
		/* CSFS: Added a back-pointer to each of these so they can use the Interface's
		 * centralised GetResource method.
		 */
		
		ChangeColours();
		
		// Create the user logging object if we are suppose to.
		// (ACL) Used to be done following ChangeAlphabet, with comment:
		// "We wait until now so we have the real value of the parameter and not just the default."
		// - hope it's ok to do before ChangeAlphabet, don't see any reason why LP_USER_LOG_LEVEL_MASK
		// would be changed?!?!
		
		int iUserLogLevel = (int)GetLongParameter(Elp_parameters.LP_USER_LOG_LEVEL_MASK);
		if (iUserLogLevel > 0) 
			m_UserLog = new CUserLog(this, m_SettingsStore, iUserLogLevel);
		
		ChangeAlphabet();
		
		CreateModules();
		CreateInput();
		CreateInputFilter();
		
		// All the setup is done by now, so let the user log object know
		// that future parameter changes should be logged.
		if (m_UserLog != null) m_UserLog.InitIsDone();
			
	}
	
	/**
	 * Instructs all componenets to unregister themselves with the
	 * event handler, and nulls our pointers to them, such that
	 * they will be available for garbage collection.
	 */
	public void DestroyInterface() {
				
		if(m_DasherModel != null) {
			m_DasherModel.UnregisterComponent();
		}
		  m_DasherModel = null;        // The order of some of these deletions matters
		  m_DasherView = null;
		  m_ColourIO = null;
		  m_AlphIO = null;
		  m_Colours = null;
		  if(m_InputFilter != null) m_InputFilter.UnregisterComponent();
		  m_InputFilter = null;
		  // FIXME Decide what needs happen to these
		  // Do NOT delete Edit box or Screen. This class did not create them.

		  // When we destruct on shutdown, we'll output any detailed log file
		  if (m_UserLog != null)
		  {
		    m_UserLog.OutputFile();
		    m_UserLog.Close();
		    // FIXME again do what's appropriate
		    m_UserLog = null;
		  }

		  if (g_Logger != null) {
		    g_Logger.Destroy();
		    g_Logger = null;
		  }

	}
	
	/**
	 * Notifies the interface before a string parameter is changed.
	 * <p>
	 * This enables the interface to read the parameter's current value if necessary.
	 * <p>
	 * Presently the interface responds to:
	 * <p><i>SP_ALPHABET_ID</i>: Stores a history of previous used alphabets
	 * in SP_ALPHABET_1, SP_ALPHABET_2 and so on.
	 * 
	 * @param iParameter Parameter which is going to change.
	 * @param sNewValue Value to which it will change.
	 */
	public void PreSetNotify(Esp_parameters iParameter, String sNewValue) {
		
		// FIXME - make this a more general 'pre-set' event in the message
		// infrastructure
		
		if(iParameter == Esp_parameters.SP_ALPHABET_ID) { 
			// Cycle the alphabet history
			if(GetStringParameter(Esp_parameters.SP_ALPHABET_ID) != sNewValue) {
				if(GetStringParameter(Esp_parameters.SP_ALPHABET_1) != sNewValue) {
					if(GetStringParameter(Esp_parameters.SP_ALPHABET_2) != sNewValue) {
						if(GetStringParameter(Esp_parameters.SP_ALPHABET_3) != sNewValue)
							SetStringParameter(Esp_parameters.SP_ALPHABET_4, GetStringParameter(Esp_parameters.SP_ALPHABET_3));
						
						SetStringParameter(Esp_parameters.SP_ALPHABET_3, GetStringParameter(Esp_parameters.SP_ALPHABET_2));
					}
					
					SetStringParameter(Esp_parameters.SP_ALPHABET_2, GetStringParameter(Esp_parameters.SP_ALPHABET_1));
				}
				
				SetStringParameter(Esp_parameters.SP_ALPHABET_1, GetStringParameter(Esp_parameters.SP_ALPHABET_ID));
			}
			
		}	
		
	}
	
	/**
	 * Override to process event by ourselves, after (as per superclass)
	 * dispatching to all registered listeners/components.
	 * <p>
	 * The interface responds to the following parameter changes:
	 * <p>
	 * <i>BP_COLOUR_MODE</i>: Calls Start() and redraws the display.
	 * <p>
	 * <i>BP_OUTLINE_MODE</i>: Redraws the display.
	 * <p>
	 * <i>BP_CONNECT_LOCK</i>: Sets the internal m_bConnectLock variable.
	 * <p>
	 * <i>LP_ORIENTATION</i>: Sets the LP_REAL_ORIENTATION parameter either
	 * to the value of LP_ORIENTATION, or if the latter is -2 (a special sentinel value)
	 * queries the current alphabet for its preferred orientation, and
	 * sets LP_REAL_ORIENTATION appropriately.
	 * <p>
	 * <i>SP_ALPHABET_ID</i>: Calls ChangeAlphabet() to rebuild the DasherModel
	 * appropriately, and then Start().
	 * <p>
	 * <i>SP_COLOUR_ID</i>: Calls ChangeColours() to insert the new colour scheme.
	 * <p>
	 * <i>BP_PALETTE_CHANGE and SP_DEFAULT_COLOUR_ID</i>: If Palette Change is true,
	 * changes COLOUR_ID to match DEFAULT_COLOUR_ID which contains the current
	 * alphabet's preferred colour scheme.
	 * <p>
	 * <i>LP_LANGUAGE_MODEL_ID</i>: Runs CreateDasherModel() to rebuild the model
	 * based on our newly chosen language model.
	 * <p>
	 * <i>SP_LM_HOST</i>: If we are currently using a remote language model,
	 * rebuilds the Model as above; otherwise, ignores.
	 * <p>
	 * <i>LP_DASHER_FONTSIZE and LP_LINE_WIDTH</i>: Redraws the display.
	 * <p>
	 * <i>SP_INPUT_FILTER</i>: Runs CreateInputFilter() to recreate the
	 * requested filter.
	 * <p>
	 * <i>SP_INPUT_DEVICE</i>: Runs CreateInput() to create the requested
	 * input device.
	 * <p>
	 * It also responds to LockEvents by setting m_bGlobalLock to the value
	 * indicated.
	 * 
	 * @param Event The event the interface is to process.
	 */
	public void InsertEvent(CEvent Event) {
		super.InsertEvent(Event);
		
		if(Event instanceof CParameterNotificationEvent) {
			CParameterNotificationEvent Evt = ((CParameterNotificationEvent)(Event));
			
			if(Evt.m_iParameter == Ebp_parameters.BP_COLOUR_MODE) {       // Forces us to redraw the display
				// TODO) { Is this variable ever used any more?
				Start();
				Redraw(true);
			} else if(Evt.m_iParameter ==  Ebp_parameters.BP_OUTLINE_MODE) {
				Redraw(true);
			} else if(Evt.m_iParameter == Ebp_parameters.BP_CONNECT_LOCK) {
				m_bConnectLock = GetBoolParameter(Ebp_parameters.BP_CONNECT_LOCK);
			} else if(Evt.m_iParameter ==  Esp_parameters.SP_ALPHABET_ID) {
				ChangeAlphabet();
				Start();
				Redraw(true);
			} else if(Evt.m_iParameter ==  Esp_parameters.SP_COLOUR_ID) {
				ChangeColours();
				Redraw(true);
			} else if(Evt.m_iParameter == Ebp_parameters.BP_PALETTE_CHANGE || Evt.m_iParameter == Esp_parameters.SP_DEFAULT_COLOUR_ID) { 
				if(GetBoolParameter(Ebp_parameters.BP_PALETTE_CHANGE))
					SetStringParameter(Esp_parameters.SP_COLOUR_ID, GetStringParameter(Esp_parameters.SP_DEFAULT_COLOUR_ID));
			} else if(Evt.m_iParameter == Elp_parameters.LP_LANGUAGE_MODEL_ID) {
				CreateDasherModel();
				Start();
				Redraw(true);
			} else if(Evt.m_iParameter == Esp_parameters.SP_LM_HOST) {
				if(GetLongParameter(Elp_parameters.LP_LANGUAGE_MODEL_ID) == 5) {
					CreateDasherModel();
					Start();
					Redraw(true);
				}
			} else if(Evt.m_iParameter == Elp_parameters.LP_LINE_WIDTH) {
				Redraw(false); // TODO - make this accessible everywhere
			} else if(Evt.m_iParameter == Elp_parameters.LP_DASHER_FONTSIZE) {
				// TODO - make screen a CDasherComponent child?
				Redraw(true);
			} else if(Evt.m_iParameter == Esp_parameters.SP_INPUT_DEVICE) {
				CreateInput();
			} else if(Evt.m_iParameter == Esp_parameters.SP_INPUT_FILTER) {
				CreateInputFilter();
			}
		}
		else if(Event instanceof CControlEvent) {
			/* CControlEvent ControlEvent = ((CControlEvent)(Event));
			
			switch(ControlEvent.m_iID) {
			case CControlManager.CTL_STOP:
				PauseAt(0,0);
			break;
			case CControlManager.CTL_PAUSE:
				Halt();
			break;
			} */
			
			// CSFS: Do nothing for the time being until Control Mode is brought back
		}
		else if(Event instanceof CLockEvent) {
			// TODO: 'Reference counting' for locks?
			CLockEvent LockEvent = ((CLockEvent)(Event));
			m_bGlobalLock = LockEvent.m_bLock;
		}
	}
	
	/**
	 * Creates a new DasherModel, deleting any previously existing
	 * one if necessary.
	 * <p>
	 * The DasherModel does most of the actual initialisation work,
	 * so see the constructor documentation for DasherModel for details.
	 * <p>
	 * This function also trains the newly created model using the
	 * using the new Alphabet's specified training text. 
	 */
	public void CreateDasherModel() 
	{
		if(m_AlphIO == null)
			return;
		
		// TODO: Move training into model?
		// TODO: Do we really need to check for a valid language model?
		int lmID = (int)GetLongParameter(Elp_parameters.LP_LANGUAGE_MODEL_ID);
		if( lmID == -1 ) return;
			
		// Train the new language model
		CLockEvent evt = new CLockEvent("Training Dasher", true, 0); 
		InsertEvent(evt);
		
		// Delete the old model and create a new one
		if(m_DasherModel != null) {
			m_DasherModel.UnregisterComponent();
		}
		
		m_DasherModel = new CDasherModel(this, m_SettingsStore, m_AlphIO, m_UserLog);
		
		// SP_TRAIN_FILE parameter set by CDasherModel constructor... 
		train(GetStringParameter(Esp_parameters.SP_TRAIN_FILE),evt);
		
		evt.m_bLock = false;
		InsertEvent(evt);
	}
	
	/**
	 * Simply calls DasherModel.Start() and DasherView.ResetYAutoOffset()
	 * so see these functions' documentation for detail. 
	 */
	public void Start() {
		// TODO: Clarify the relationship between Start() and
		// InvalidateContext() - I believe that they essentially do the same
		// thing
		PauseAt(0, 0);
		if(m_DasherModel != null) {
			//ACL preserving behaviour etc., but this doesn't look right...use start-of-sentence ctx?
			m_DasherModel.SetContext("");
		}
	}
	
	/**
	 * Pauses Dasher at a given mouse location, and sets
	 * BP_REDRAW so that a full redraw will be performed next
	 * frame.
	 * <p>
	 * Also generates a StopEvent to notify other components.
	 * 
	 * @param MouseX Mouse x co-ordinate at the time of stopping
	 * @param MouseY Mouse y co-ordinate at the time of stopping
	 */
	public void PauseAt(int MouseX, int MouseY) {
		SetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED, true);
		
		// Request a full redraw at the next time step.
		SetBoolParameter(Ebp_parameters.BP_REDRAW, true);
		
		CStopEvent oEvent = new CStopEvent();
		InsertEvent(oEvent);
		
		if (m_UserLog != null)
			m_UserLog.StopWriting((float) GetNats());
	}
	
	/**
	 * Pauses Dasher and calls DasherModel.Halt().
	 * <p>
	 * This function, unlike PauseAt, does not throw a StopEvent. 
	 */	
	public void Halt() {
		SetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED, true);
		
		if(GetBoolParameter(Ebp_parameters.BP_MOUSEPOS_MODE)) {
			SetLongParameter(Elp_parameters.LP_MOUSE_POS_BOX, 1);
		}
		
		// This will cause us to reinitialise the frame rate counter - ie we start off slowly
		if(m_DasherModel != null)
			m_DasherModel.Halt();
	}
	
	/**
	 * Unpause Dasher. This will send a StartEvent to all
	 * components.
	 * 
	 * @param Time System time as a UNIX timestamp at which Dasher was restarted.
	 */
	public void Unpause(long Time) { // CSFS: Formerly unsigned.
		SetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED, false);
		
		if(m_DasherModel != null) {
			m_DasherModel.Reset_framerate(Time);
			//m_pDasherModel->Set_paused(m_Paused);
		}
				
		CStartEvent oEvent = new CStartEvent();
		InsertEvent(oEvent);
		
		ResetNats();
		if (m_UserLog != null)
			m_UserLog.StartWriting();
	}
	
	/**
	 * Creates an input device by calling GetModuleByName on the parameter
	 * SP_INPUT_DEVICE. In the event that this does not correspond
	 * to a module known by the Module Manager, m_Input will
	 * be set to null.
	 * <p>
	 * If there is an existing input device, it will be Unref'd and
	 * Deactivated first.
	 * <p>
	 * In the event that a non-null DasherInput class is created,
	 * its Ref and Activate methods will be called immediately.
	 * 
	 * @see CDasherInput
	 * @see CModuleManager
	 *
	 */	
	public void CreateInput() {
		
		// FIXME - this shouldn't be the model used here - we should just change a parameter and work from the appropriate listener
		
		if(m_Input != null) {
			m_Input.Deactivate();
			m_Input.Unref();
		}
		
		m_Input = (CDasherInput)GetModuleByName(GetStringParameter(Esp_parameters.SP_INPUT_DEVICE));
		
		if(m_Input != null) {
			m_Input.Ref();
			m_Input.Activate();
		}
		
		if(m_DasherView != null)
			m_DasherView.SetInput(m_Input);
	}
	
	/**
	 * Encapsulates the entire process of drawing a
	 * new frame of the Dasher world.
	 * <p>
	 * We invoke our input filter's Timer method, which determines
	 * in what way the Model should be updated, if at all, allow the
	 * Model to check its consistency after being changed, and then
	 * finally ask our View to draw the newly updated world.
	 * <p>
	 * Method will return without any action if any of the three
	 * lock variables are true.
	 * 
	 * @param iTime Current system time as a UNIX time stamp.
	 */
	public void NewFrame(long iTime) {
		// Fail if Dasher is locked
		
		
		if(m_bGlobalLock || m_bShutdownLock || m_bConnectLock) return;
			
			boolean bChanged = false;
		
		
		if(m_DasherView != null) {
			if(!GetBoolParameter(Ebp_parameters.BP_TRAINING)) {
				if (m_UserLog != null) {
					
					ArrayList<CSymbolProb> vAdded = new ArrayList<CSymbolProb>();
					int iNumDeleted = 0;
					
					if(m_InputFilter != null) {
						bChanged = m_InputFilter.Timer(iTime, m_DasherView, m_DasherModel); // FIXME - need logging stuff here
					}
					
					if (iNumDeleted > 0)
						m_UserLog.DeleteSymbols(iNumDeleted);
						if (vAdded.size() > 0)
							m_UserLog.AddSymbols(vAdded);
							
				}
				else {
					if(m_InputFilter != null) {
						bChanged = m_InputFilter.Timer(iTime, m_DasherView, m_DasherModel);
					}
				}
				
				m_DasherModel.CheckForNewRoot(m_DasherView);
			}
		}
		
		Draw(bChanged);
		
		// This just passes the time through to the framerate tracker, so we
		// know how often new frames are being drawn.
		if(m_DasherModel != null)
			m_DasherModel.NewFrame(iTime);
	}
	
	/**
	 * Completely draws the screen based on the current state
	 * of the model, taking into account whether the model
	 * has changed since the last frame.
	 * <p>
	 * This breaks down to calling the Model's RenderToView
	 * method if we need to draw the nodes, and then calling the
	 * InputFilter's DecorateView method.
	 * <p>
	 * The View's Display() method is called only in the event
	 * that anything has changed.
	 * <p>
	 * At present, this is hacked such that it always does a full
	 * redraw if Dasher is unpaused, owing to the fact that the
	 * Java front-end must receive a frame every time it calls
	 * Draw. See the documentation for applet.JDasherScreen for details.
	 *  
	 * @param bRedrawNodes True if the Model has changed since the last frame.
	 * @see dasher.applet.JDasherScreen
	 */
	public void Draw(boolean bRedrawNodes) {
		
		if(m_DasherView == null || m_DasherModel == null)
			return;
		if (m_MarkerScreen!=null) {
			if (bRedrawNodes) {
				m_MarkerScreen.SendMarker(0);
				m_DasherModel.RenderToView(m_DasherView);
			}
			m_MarkerScreen.SendMarker(1);
			boolean bDecorationsChanged = (m_InputFilter != null) &&
				m_InputFilter.DecorateView(m_DasherView);
			if (bRedrawNodes || bDecorationsChanged)
				m_MarkerScreen.Display();
		} else {
			//simple screen, no markers / caching, render whole frame every time
			m_DasherModel.RenderToView(m_DasherView);
			if (m_InputFilter!=null) m_InputFilter.DecorateView(m_DasherView);
		}
	}
	
	/**
	 * Abstract method which will be called whenever a screen
	 * redraw is required. This is implemented for architectures
	 * which require drawing to happen in a top-down fashion.
	 * Implementations which allow drawing to be initiated from
	 * the bottom up may do so my recalling Draw.
	 * 
	 * @param bChanged True if the model has changed since the last frame
	 */
	public abstract void Redraw(boolean bChanged);
	
	/**
	 * Changes the alphabet in use to that described by SP_ALPHABET_ID.
	 * <p>
	 * Writes the training file to disk using WriteTrainFileFull,
	 * and then runs CreateDasherModel, deleting an exsiting Model
	 * if it exists.
	 * <p>
	 * In the event that SP_ALPHABET_ID is empty when this function
	 * is called, it is set to the default suggested by m_AlphIO.
	 */
	public void ChangeAlphabet() {
		
		if(GetStringParameter(Esp_parameters.SP_ALPHABET_ID) == "") {
			SetStringParameter(Esp_parameters.SP_ALPHABET_ID, m_AlphIO.GetDefault());
			// This will result in ChangeAlphabet() being called again, so
			// exit from the first recursion
			return;
		}
		
		// Send a lock event
		
		WriteTrainFileFull();
		
		// Lock Dasher to prevent changes from happening while we're training.
		
		SetBoolParameter( Ebp_parameters.BP_TRAINING, true );
		
		if(m_DasherModel != null) {
			m_DasherModel.UnregisterComponent();
			m_DasherModel = null;
		}
				
		CreateDasherModel();
		
		SetBoolParameter( Ebp_parameters.BP_TRAINING, false );
		
		Start();
		
	}
	
	/**
	 * Changes the colour scheme to that described by SP_COLOUR_ID.
	 * <p>
	 * If m_ColourIO is null at the time, this method will return
	 * without performing any action.
	 * <p>
	 * Specifically, this method retrieves the colour scheme named
	 * by SP_COLOUR_ID from m_ColourIO, and creates a new CustomColours
	 * wrapping it, finally setting m_Colours to point to the new
	 * scheme. Finally, if successful, the screen is informed of the
	 * new scheme by calling its SetColourScheme method.
	 */
	public void ChangeColours() {
		if(m_ColourIO == null)
			return;
		
		if(m_Colours != null) {
			m_Colours = null;
		}
		
		
		CColourIO.ColourInfo oColourInfo = (m_ColourIO.GetInfo(GetStringParameter(Esp_parameters.SP_COLOUR_ID)));
		m_Colours = new CCustomColours(oColourInfo);
		
		if(m_DasherScreen != null) {
			m_DasherScreen.SetColourScheme(m_Colours);
		}
	}
	
	/**
	 * Changes the Screen to which we should send drawing instructions.
	 * <p>
	 * If a view already exists, it is notified of the new screen.
	 * <p>
	 * If no view exists, one is created by a call to ChangeView.
	 * 
	 * @param NewScreen New screen
	 */
	public void ChangeScreen(CDasherScreen NewScreen) {
		m_DasherScreen = NewScreen;
		m_MarkerScreen = (NewScreen instanceof CMarkerScreen) ? (CMarkerScreen)NewScreen : null;
		m_DasherScreen.SetColourScheme(m_Colours);
		
		if(m_DasherView != null) {
			m_DasherView.ChangeScreen(m_DasherScreen);
		} else {
			if(GetLongParameter(Elp_parameters.LP_VIEW_ID) != -1)
				ChangeView();
		}
		
		Redraw(true);
	}
	
	/**
	 * Creates an instance of DasherViewSquare, and, if they are present,
	 * informs it of our current Input Filter and Screen.
	 * <p>
	 * This ought to respond to the LP_VIEW_ID parameter, but since
	 * at present there is only one, it is ignored for now. 
	 *
	 */
	public void ChangeView() {
		// TODO: Actually respond to LP_VIEW_ID parameter (although there is only one view at the moment)
		
		if(m_DasherScreen != null && m_DasherModel != null) 
		{
			m_DasherView = null;
			m_DasherView = new CDasherViewSquare(this, m_SettingsStore, m_DasherScreen);
			
			
			if (m_Input != null)
				m_DasherView.SetInput(m_Input);
		}
	}
	
	/**
	 * Deferred to m_AlphIO
	 * 
	 * @see CAlphIO
	 */
	public void GetAlphabets(ArrayList<String> AlphabetList) {
		m_AlphIO.GetAlphabets(AlphabetList);
	}
	
	/**
	 * Deferred to m_AlphIO
	 * 
	 * @see CAlphIO
	 */
	public CAlphIO.AlphInfo GetInfo(String AlphID) {
		return m_AlphIO.GetInfo(AlphID);
	}
	
	/**
	 * Deferred to m_AlphIO
	 * 
	 * @see CAlphIO
	 */
	public void SetInfo(CAlphIO.AlphInfo NewInfo) {
		m_AlphIO.SetInfo(NewInfo);
	}
	
	/**
	 * Deferred to m_AlphIO
	 * 
	 * @see CAlphIO
	 */
	public void DeleteAlphabet(String AlphID) {
		m_AlphIO.Delete(AlphID);
	}
	
	/**
	 * Deferred to m_ColourIO
	 * 
	 * @see CColourIO
	 */
	public void GetColours(ArrayList<String> ColourList) {
		m_ColourIO.GetColours(ColourList);
	}
	
	/** Subclasses should implement to train the model with both user &amp; system texts.
	 * Progress updates are an optional extra for subclasses, albeit desirable.
	 * 
	 * @param T alphabet-provided name of training file, e.g. "training_english_GB.txt"
	 * @param evt Event to use to notify of progress updates (null =&gt; no updates) by filling in m_iPercent field
	 */
	protected abstract void train(String T, CLockEvent evt);
	
	/**
	 * Retrieves a list of available font sizes. This class
	 * returns a generic reasonably sensible list 
	 * (11, 14, 20, 22, 28, 40, 44, 56, 80) but should be
	 * overridden by the implementing class if a better answer
	 * can be retrieved.
	 * 
	 * @param FontSizes Collection to be filled with available sizes
	 */
	public void GetFontSizes(Collection<Integer> FontSizes) {
		FontSizes.add(20);
		FontSizes.add(14);
		FontSizes.add(11);
		FontSizes.add(40);
		FontSizes.add(28);
		FontSizes.add(22);
		FontSizes.add(80);
		FontSizes.add(56);
		FontSizes.add(44);
	}
	
	/**
	 * Stub. Ought to return the current characters per minute,
	 * but this is not yet implemented.
	 *  
	 * @return 0
	 */
	public double GetCurCPM() {
		//
		return 0;
	}
	
	/**
	 * ACL TODO - was "not yet implemented", so trying returning the model's
	 * {@link CDasherModel#Framerate()}.
	 */
	public double GetCurFPS() {
		return m_DasherModel.Framerate();
	}
	
	/**
	 * Deferred to CDasherModel
	 * 
	 * @see CDasherModel
	 */
	public double GetNats() {
		if(m_DasherModel != null)
			return m_DasherModel.GetNats();
			else
				return 0.0;
	}
	
	/**
	 * Passed on to CDasherModel, if it is non-null.
	 * 
	 * @see CDasherModel
	 */
	public void ResetNats() {
		if(m_DasherModel != null)
			m_DasherModel.ResetNats();
	}
	
	/**
	 * Attempts to retrieve a fresh context by dispatching an
	 * EditContextEvent, which has a field to return a new context
	 * if anybody hearing the event chose to submit one.
	 * <p>
	 * If bForceStart is true, or we received a non-empty context,
	 * the Model is fed the new Context using SetContext and is
	 * then paused.
	 * <p>
	 * Finally there is a mandatory screen redraw.
	 * 
	 * @param bForceStart Should we rebuild the context even if none is submitted?
	 */
	public void InvalidateContext(boolean bForceStart) {
			
		/* CSFS: This used to clear m_DasherModel.strContextBuffer,
		 * which has been removed per the notes at the top of the file.
		 */
		//ACL couldn't find said notes! But changing context system anyway.
		// The previous code seemed to extract the most recent 10 characters,
		// so I'm preserving that behaviour!
		StringBuilder strLast = new StringBuilder(10);
		for (ListIterator<Character> it = charactersEntered(); it.hasPrevious() && strLast.length()<10;)
			strLast.append(it.previous());
		//we build the string in reverse order, so reverse it now...
		String strNewContext = strLast.reverse().toString();
		strTrainfileBuffer.append(strNewContext);
						
		if(bForceStart || charactersEntered().hasPrevious()) {
			if(m_DasherModel != null) {
				if(m_DasherModel.m_bContextSensitive || bForceStart) {
					m_DasherModel.SetContext(strNewContext);
					PauseAt(0,0);
				}
			}
		}
		
		if(m_DasherView != null)
			m_DasherModel.CheckForNewRoot(m_DasherView);
		
		Redraw(true);
		
	}
	
	
	/* public void SetContext(String strNewContext) {
		m_DasherModel.m_strContextBuffer = strNewContext;
	} */
	
	// REMOVED: See bug notes at the top.
	
//	Control mode stuff
	
	/**
	 * Deferred to m_DasherModel.
	 * 
	 * @see CDasherModel
	 */
	public void RegisterNode( int iID, String strLabel, int iColour ) {
		m_DasherModel.RegisterNode(iID, strLabel, iColour);
	}
	
	/**
	 * Deferred to m_DasherModel.
	 * 
	 * @see CDasherModel
	 */
	public void ConnectNode(int iChild, int iParent, int iAfter) {
		m_DasherModel.ConnectNode(iChild, iParent, iAfter);
	}
	
	/**
	 * Deferred to m_DasherModel.
	 * 
	 * @see CDasherModel
	 */
	public void DisconnectNode(int iChild, int iParent) {
		m_DasherModel.DisconnectNode(iChild, iParent);
	}
	
	/**
	 * Deferred to CSettingsStore
	 * 
	 * @see CSettingsStore
	 * 
	 */
	public void SetBoolParameter(Ebp_parameters iParameter, boolean bValue) {
		m_SettingsStore.SetBoolParameter(iParameter, bValue);
	}
	
	/**
	 * Deferred to CSettingsStore
	 * 
	 * @see CSettingsStore
	 * 
	 */
	public void SetLongParameter(Elp_parameters iParameter, long lValue) { 
		m_SettingsStore.SetLongParameter(iParameter, lValue);
	};
	
	/**
	 * Deferred to CSettingsStore
	 * 
	 * @see CSettingsStore
	 * 
	 */
	public void SetStringParameter(Esp_parameters iParameter, String sValue) {
		PreSetNotify(iParameter, sValue);
		m_SettingsStore.SetStringParameter(iParameter, sValue);
	};
	
	/**
	 * Deferred to CSettingsStore
	 * 
	 * @see CSettingsStore
	 * 
	 */
	public boolean GetBoolParameter(Ebp_parameters iParameter) {
		return m_SettingsStore.GetBoolParameter(iParameter);
	}
	
	/**
	 * Deferred to CSettingsStore
	 * 
	 * @see CSettingsStore
	 * 
	 */
	public long GetLongParameter(Elp_parameters iParameter) {
		return m_SettingsStore.GetLongParameter(iParameter);
	}
	
	/**
	 * Deferred to CSettingsStore
	 * 
	 * @see CSettingsStore
	 * 
	 */
	public String GetStringParameter(Esp_parameters iParameter) {
		return m_SettingsStore.GetStringParameter(iParameter);
	}
	
	/**
	 * Deferred to CSettingsStore
	 * 
	 * @see CSettingsStore
	 * 
	 */
	public void ResetParameter(EParameters iParameter) {
		m_SettingsStore.ResetParameter(iParameter);
	}
	
	/**
	 * Gets a reference to m_UserLog.
	 * 
	 * @return m_UserLog
	 */
	public CUserLog GetUserLogPtr() {
		return m_UserLog;
	}
	
	/**
	 * Signals a key press to our input filter. This should be invoked
	 * by an implementation whenever a key is pressed.
	 * <p>
	 * Key presses signalled in this way are ignored if we have
	 * no input filter, or if BP_TRAINING is true.
	 * <p>
	 * Currently assigned key IDs:
	 * <p><ul>
	 * <li>0: Start/stop (keyboard)
	 * <li>1: Move east (for button modes)
	 * <li>2: Move north
	 * <li>3: Move west
	 * <li>4: Move south
	 * <li>100: Mouse click
	 * </ul>
	 * <p>
	 * The actual physical keys assigned to these functions
	 * are down to the implementation, and may be user-definable.
	 * 
	 * @param iTime System time as a UNIX timestamp at which the key was pressed
	 * @param iId Identifier of the pressed key
	 */
	public void KeyDown(long iTime, int iId) {
		if(m_InputFilter != null && !GetBoolParameter(Ebp_parameters.BP_TRAINING)) {
			m_InputFilter.KeyDown(iTime, iId, m_DasherModel);
		}
	}
	
	/**
	 * Signals a key press to our input filter. This should be invoked
	 * by an implementation whenever a key is released.
	 * <p>
	 * Key presses signalled in this way are ignored if we have
	 * no input filter, or if BP_TRAINING is true.
	 * <p>
	 * Currently assigned key IDs:
	 * <p><ul>
	 * <li>0: Start/stop
	 * <li>1: Move east (for button modes)
	 * <li>2: Move north
	 * <li>3: Move west
	 * <li>4: Move south
	 * <li>100: Left mouse click (or equivalent)
	 * </ul>
	 * <p>
	 * The actual physical keys assigned to these functions
	 * are down to the implementation, and may be user-definable.
	 * 
	 * @param iTime System time as a UNIX timestamp at which the key was pressed
	 * @param iId Identifier of the pressed key
	 */
	public void KeyUp(long iTime, int iId) {
		if(m_InputFilter != null && !GetBoolParameter(Ebp_parameters.BP_TRAINING)) {
			m_InputFilter.KeyUp(iTime, iId, m_DasherModel);
		}
	}
	
	/**
	 * Creates m_InputFilter by retrieving the module named
	 * in SP_INPUT_FILTER.
	 * <p>
	 * If this is successful and an input filter is created,
	 * it will be Ref'd and Activated immediately.
	 * <p>
	 * If unsuccessful, m_InputFilter will be set to null.
	 * <p>
	 * If there is an existing filter, it is Deactivated and
	 * Unref'd first.
	 *
	 */
	public void CreateInputFilter()
	{
		if(m_InputFilter != null) {
			m_InputFilter.Deactivate();
			m_InputFilter.Unref();
			m_InputFilter = null;
		}
		
		m_InputFilter = (CInputFilter)GetModuleByName(GetStringParameter(Esp_parameters.SP_INPUT_FILTER));
		
		if(m_InputFilter != null) {
			m_InputFilter.Ref();
			m_InputFilter.Activate();
		}
	}
	
	/**
	 * Deferred to m_oModuleManager
	 * 
	 * @see CModuleManager
	 */
	public void RegisterModule(CDasherModule mod) {
		m_oModuleManager.RegisterModule(mod);
	}
	
	/**
	 * Deferred to m_oModuleManager
	 * 
	 * @see CModuleManager
	 */
	public CDasherModule GetModule(long iID) {
		return m_oModuleManager.GetModule(iID);
	}
	
	/**
	 * Deferred to m_oModuleManager
	 * 
	 * @see CModuleManager
	 */
	public CDasherModule GetModuleByName(String strName) {
		return m_oModuleManager.GetModuleByName(strName);
	}
	
	/**
	 * Manually registers a number of input filters.
	 * <p>
	 * At present this registers only Normal Control and Click Mode,
	 * but as an when others are implemented will register these also.
	 * Subclasses can & should override if they want anything different
	 * or extra.
	 * <p>
	 * The same input filter may be registered repeatedly under
	 * a variety of different names if desired (so long as the
	 * filter's constructor permits a user-defined name).
	 * <p>
	 * This is commonly used to produce a number of different
	 * button modes which use the same filter class.
	 *
	 */
	protected void CreateModules() {
		RegisterModule(new CDefaultFilter(this, m_SettingsStore, 3, "Normal Control"));
		//RegisterModule(new COneDimensionalFilter(this, m_SettingsStore, this, m_DasherModel));
		//RegisterModule(new CEyetrackerFilter(this, m_SettingsStore, this, m_DasherModel));
		RegisterModule(new CClickFilter(this, m_SettingsStore));
		//RegisterModule(new CDynamicFilter(this, m_SettingsStore, this));
		//RegisterModule(new CTwoButtonDynamicFilter(this, m_SettingsStore, this));
		// TODO: specialist factory for button mode
		//RegisterModule(new CDasherButtons(this, m_SettingsStore, this, 5, 1, true,8, "Menu Mode"));
		//RegisterModule(new CDasherButtons(this, m_SettingsStore, this, 3, 0, false,10, "Direct Mode"));
		//  RegisterModule(new CDasherButtons(this, m_SettingsStore, this, 4, 0, false,11, "Buttons 3"));
		//RegisterModule(new CDasherButtons(this, m_SettingsStore, this, 3, 3, false,12, "Alternating Direct Mode"));
		//RegisterModule(new CDasherButtons(this, m_SettingsStore, this, 4, 2, false,13, "Compass Mode"));
		//RegisterModule(new CStylusFilter(this, m_SettingsStore, this, m_DasherModel,15, "Stylus Control")); 
		
		/* CSFS: The commented out filters are not yet implemented. */
		
	}
	
	/* CSFS: This is worrisome, it looks as if all identifiers and their associated
	 * functions may need to be set to use UTF-8, ie. prevented from using Java
	 * Strings. Commenting out for the time being since I won't be using GetValues
	 * until much later in testing.
	 */
	
	/* public void GetPermittedValues(int iParameter, ArrayList<String> vList) {
		// TODO: Deprecate direct calls to these functions
		switch (iParameter) {
		case Esp_parameters.SP_ALPHABET_ID:
			GetAlphabets(vList);
			break;
		case Esp_parameters.SP_COLOUR_ID:
			GetColours(vList);
			break;
		case Esp_parameters.SP_INPUT_FILTER:
			m_oModuleManager.ListModules(1, vList);
			break;
		case Esp_parameters.SP_INPUT_DEVICE:
			m_oModuleManager.ListModules(0, vList);
			break;
		}
	} */
	
	/**
	 * Engages the shutdown lock (m_bShutdownLock).
	 */
	public void StartShutdown() {
		m_bShutdownLock = true;
	}
	
	
	/**
	 * Writes the text entered by the user as a training file.
	 * <p>
	 * Won't work at present, as WriteTrainFile() is stubbed.
	 *
	 */
	protected void WriteTrainFileFull() {
		WriteTrainFile(strTrainfileBuffer.toString());
		strTrainfileBuffer.setLength(0);
	}
	
	/**
	 * Writes the first 100 characters of user-entered text
	 * as a training file.
	 * <p>
	 * Won't work at the moment, as WriteTrainFile() is stubbed.
	 *
	 */
	protected void WriteTrainFilePartial() {
		// TODO: what if we're midway through a unicode character?
		
		if(strTrainfileBuffer.length() > 100) {
			//LinkedList<Byte> surplus = new LinkedList<Byte>();
			//while(strTrainfileBuffer.length() > 100) {
			//	surplus.addFirst(strTrainfileBuffer.getLast());
			//	strTrainfileBuffer.removeLast();
			//}
			WriteTrainFile(strTrainfileBuffer.substring(0, 99));
			strTrainfileBuffer.delete(0, 99);
			// strTrainfileBuffer.addAll(surplus);
			
			/* CSFS: Write the first 100 bytes to disk and keep the rest */
		}
		else {
			WriteTrainFile(strTrainfileBuffer.toString());
			strTrainfileBuffer.setLength(0);
		}
		
	}
	
	/**
	 * Stub. This ought to write out a training file, but
	 * is not implemented at present due since the applet
	 * implementation cannot write a training file to the
	 * local disk.
	 * <p>
	 * This method should be implemented if a local application
	 * version of JDasher is to be implemented, and should write
	 * a file containing the user's typed text encoded as UTF-8
	 * suitable for reading by the TrainStream routine.
	 * 
	 * @param strNewText
	 */
	public void WriteTrainFile(String strNewText) {
		/* Empty method: at the platform-independent level
		 * we can't know how to write the file.
		 */
	}
	
	/**
	 * This will be called by a number of file I/O methods when
	 * ordinary file I/O fails; it is intended for implementations
	 * to override it if they wish to return a stream pointing to
	 * JAR-packed files, remote network locations, etc.
	 * <p>
	 * By default it ignores its arguments and returns null.
	 * <p>
	 * Overriding is optional; ordinary file I/O will still be
	 * tried before calling this method.
	 * 
	 * @param filename File to be opened
	 * @return InputStream if it could be opened, or null if not.
	 */
	public InputStream getResourceStream(String filename) {
		return null;
	}
	
}
