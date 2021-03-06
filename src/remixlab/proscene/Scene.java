/**************************************************************************************
 * ProScene (version 2.1.0)
 * Copyright (c) 2010-2014 National University of Colombia, https://github.com/remixlab
 * @author Jean Pierre Charalambos, http://otrolado.info/
 * 
 * All rights reserved. Library that eases the creation of interactive scenes
 * in Processing, released under the terms of the GNU Public License v3.0
 * which is available at http://www.gnu.org/licenses/gpl.html
 **************************************************************************************/

package remixlab.proscene;

import processing.core.*;
import processing.opengl.*;
import remixlab.bias.core.*;
import remixlab.dandelion.agent.*;
import remixlab.dandelion.core.*;
import remixlab.dandelion.geom.*;
import remixlab.fpstiming.*;

import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

// begin: GWT-incompatible
///*
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
// end: GWT-incompatible
//*/

/**
 * A 2D or 3D interactive Processing Scene. The Scene is a specialization of the
 * {@link remixlab.dandelion.core.AbstractScene}, providing an interface between Dandelion and Processing.
 * <p>
 * <h3>Usage</h3>
 * To use a Scene you have three choices:
 * <ol>
 * <li><b>Direct instantiation</b>. In this case you should instantiate your own Scene object at the
 * {@code PApplet.setup()} function. See the example <i>BasicUse</i>.
 * <li><b>Inheritance</b>. In this case, once you declare a Scene derived class, you should implement
 * {@link #proscenium()} which defines the objects in your scene. Just make sure to define the {@code PApplet.draw()}
 * method, even if it's empty. See the example <i>AlternativeUse</i>.
 * <li><b>External draw handler registration</b>. In addition (not being part of Dandelion), you can even declare an
 * external drawing method and then register it at the Scene with {@link #addDrawHandler(Object, String)}. That method
 * should return {@code void} and have one single {@code Scene} parameter. This strategy may be useful when there are
 * multiple viewers sharing the same drawing code. See the example <i>StandardCamera</i>.
 * </ol>
 * <h3>Interactivity mechanisms</h3>
 * 
 * ProScene provides powerful interactivity mechanisms allowing a wide range of scene setups ranging from very simple to
 * complex ones. For convenience, two interaction mechanisms are provided by default: {@link #keyboardAgent()}, and
 * {@link #motionAgent()} (which in the desktop version of proscene defaults to a {@link #mouseAgent()}):
 * <ol>
 * <li><b>The default keyboard agent</b> provides shortcuts to Dandelion keyboard actions such as {@link #drawGrid()} or
 * {@link #drawAxes()}. See {@link #setKeyboardShortcut(Character, remixlab.dandelion.core.Constants.KeyboardAction)}
 * and {@link #setKeyboardShortcut(int, int, remixlab.dandelion.core.Constants.KeyboardAction)}.
 * <li><b>The default mouse agent</b> provides high-level methods to manage Eye and Frame motion actions. Please refer
 * to the different {@code setMouseButtonBinding()}, {@code setMouseClickBinding()}, {@code setMouseWheelBinding()}
 * methods.
 * </ol>
 * <h3>Animation mechanisms</h3>
 * ProScene provides three animation mechanisms to define how your scene evolves over time:
 * <ol>
 * <li><b>Overriding the Dandelion {@link #animate()} method.</b> In this case, once you declare a Scene derived class,
 * you should implement {@link #animate()} which defines how your scene objects evolve over time. See the example
 * <i>Animation</i>.
 * <li><b>By checking if the Dandelion AbstractScene's {@link #timer()} was triggered within the frame.</b> See the
 * example <i>Flock</i>.
 * <li><b>External animation handler registration.</b> In addition (not being part of Dandelion), you can also declare
 * an external animation method and then register it at the Scene with {@link #addAnimationHandler(Object, String)}.
 * That method should return {@code void} and have one single {@code Scene} parameter. See the example
 * <i>AnimationHandler</i>.
 */
public class Scene extends AbstractScene implements PConstants {
	// begin: GWT-incompatible
	// /*
	// Reflection
	// 1. Draw
	protected Object						drawHandlerObject;
	// The method in drawHandlerObject to execute
	protected Method						drawHandlerMethod;
	// the name of the method to handle the event
	protected String						drawHandlerMethodName;
	// 2. Animation
	// The object to handle the animation
	protected Object						animateHandlerObject;
	// The method in animateHandlerObject to execute
	protected Method						animateHandlerMethod;
	// the name of the method to handle the animation
	protected String						animateHandlerMethodName;

	// Timing
	protected boolean						javaTiming;
	// end: GWT-incompatible
	// */

	public static final String	prettyVersion	= "2.1.0";

	public static final String	version				= "23";

	// P R O C E S S I N G A P P L E T A N D O B J E C T S
	protected PApplet						parent;
	protected PGraphics					pgraphics;

	// E X C E P T I O N H A N D L I N G
	protected int								beginOffScreenDrawingCalls;

	// CONSTRUCTORS

	/**
	 * Constructor that defines an on-screen Processing Scene. Same as {@code this(p, p.g}.
	 * 
	 * @see #Scene(PApplet, PGraphics)
	 * @see #Scene(PApplet, PGraphics, int, int)
	 */
	public Scene(PApplet p) {
		this(p, p.g);
	}

	/**
	 * Same as {@code this(p, renderer, 0, 0)}.
	 * 
	 * @see #Scene(PApplet)
	 * @see #Scene(PApplet, PGraphics, int, int)
	 */
	public Scene(PApplet p, PGraphics renderer) {
		this(p, renderer, 0, 0);
	}

	/**
	 * Main constructor defining a left-handed Processing compatible Scene. Calls {@link #setMatrixHelper(MatrixHelper)}
	 * using a customized {@link remixlab.dandelion.core.MatrixHelper} depending on the {@code pg} type (see
	 * {@link remixlab.proscene.Java2DMatrixHelper} and {@link remixlab.proscene.GLMatrixHelper}). The constructor
	 * instantiates the {@link #inputHandler()} and the {@link #timingHandler()}, sets the AXIS and GRID visual hint
	 * flags, instantiates the {@link #eye()} (a {@link remixlab.dandelion.core.Camera} if the Scene {@link #is3D()} or a
	 * {@link remixlab.dandelion.core.Window} if the Scene {@link #is2D()}). It also instantiates the
	 * {@link #keyboardAgent()} and the {@link #mouseAgent()}, and finally calls {@link #init()}.
	 * <p>
	 * An off-screen Processing Scene is defined if {@code pg != p.g}. In this case the {@code x} and {@code y} parameters
	 * define the position of the upper-left corner where the off-screen Scene is expected to be displayed, e.g., for
	 * instance with a call to Processing the {@code image(img, x, y)} function. If {@code pg == p.g}) (which defines an
	 * on-screen Scene, see also {@link #isOffscreen()}), the values of x and y are meaningless (both are set to 0 to be
	 * taken as dummy values).
	 * 
	 * @see remixlab.dandelion.core.AbstractScene#AbstractScene()
	 * @see #Scene(PApplet)
	 * @see #Scene(PApplet, PGraphics)
	 */
	public Scene(PApplet p, PGraphics pg, int x, int y) {
		// 1. P5 objects
		parent = p;
		pgraphics = pg;

		// 2. Matrix helper
		if (pg instanceof PGraphics3D)
			setMatrixHelper(new GLMatrixHelper(this, (PGraphics3D) pg));
		else if (pg instanceof PGraphics2D)
			setMatrixHelper(new GLMatrixHelper(this, (PGraphics2D) pg));
		else
			setMatrixHelper(new Java2DMatrixHelper(this, pg));

		// 3. Eye
		setLeftHanded();
		width = pg.width;
		height = pg.height;
		eye = is3D() ? new Camera(this) : new Window(this);
		setEye(eye());// calls showAll();

		// 4. Off-screen?
		offscreen = pg != p.g;
		upperLeftCorner = offscreen ? new Point(x, y) : new Point(0, 0);

		// 5. Create agents and register P5 methods
		defKeyboardAgent = new KeyAgent(this, "proscene_keyboard");
		enableKeyboardAgent();
		defMotionAgent = new MouseAgent(this, "proscene_mouse");
		enableMotionAgent();
		pApplet().registerMethod("pre", this);
		pApplet().registerMethod("draw", this);
		// Misc stuff:
		setDottedGrid(!(platform() == Platform.PROCESSING_ANDROID || is2D()));
		if (platform() == Platform.PROCESSING_DESKTOP || platform() == Platform.PROCESSING_ANDROID)
			this.setNonSeqTimers();
		// pApplet().frameRate(100);

		// 6. Init should be called only once
		init();
	}

	// P5 STUFF

	/**
	 * Returns the PApplet instance this Scene is related to.
	 */
	public PApplet pApplet() {
		return parent;
	}

	/**
	 * Returns the PGraphics instance this Scene is related to. It may be the PApplets one, if the Scene is on-screen or
	 * an user-defined if the Scene {@link #isOffscreen()}.
	 */
	public PGraphics pg() {
		return pgraphics;
	}

	@Override
	public int width() {
		return pg().width;
	}

	@Override
	public int height() {
		return pg().height;
	}

	// DIM

	@Override
	public boolean is3D() {
		return (pgraphics instanceof PGraphics3D);
	}

	// CHOOSE PLATFORM

	@Override
	protected void setPlatform() {
		Properties p = System.getProperties();
		Enumeration<?> keys = p.keys();
		while (keys.hasMoreElements()) {
			String key = (String) keys.nextElement();
			String value = (String) p.get(key);
			if (key.contains("java.vm.vendor")) {
				if (Pattern.compile(Pattern.quote("Android"), Pattern.CASE_INSENSITIVE).matcher(value).find())
					platform = Platform.PROCESSING_ANDROID;
				else
					platform = Platform.PROCESSING_DESKTOP;
				break;
			}
		}
	}

	// P5-WRAPPERS

	/**
	 * Wrapper for PGraphics.vertex(x,y,z)
	 */
	public void vertex(float x, float y, float z) {
		if (this.is2D())
			pg().vertex(x, y);
		else
			pg().vertex(x, y, z);
	}

	/**
	 * Wrapper for PGraphics.vertex(x,y)
	 */
	public void vertex(float x, float y) {
		pg().vertex(x, y);
	}

	/**
	 * Wrapper for PGraphics.line(x1, y1, z1, x2, y2, z2)
	 */
	public void line(float x1, float y1, float z1, float x2, float y2, float z2) {
		if (this.is2D())
			pg().line(x1, y1, x2, y2);
		else
			pg().line(x1, y1, z1, x2, y2, z2);
	}

	/**
	 * Wrapper for PGraphics.line(x1, y1, x2, y2)
	 */
	public void line(float x1, float y1, float x2, float y2) {
		pg().line(x1, y1, x2, y2);
	}

	/**
	 * Converts a {@link remixlab.dandelion.geom.Vec} to a PVec.
	 */
	public static PVector toPVector(Vec v) {
		return new PVector(v.x(), v.y(), v.z());
	}

	/**
	 * Converts a PVec to a {@link remixlab.dandelion.geom.Vec}.
	 */
	public static Vec toVec(PVector v) {
		return new Vec(v.x, v.y, v.z);
	}

	/**
	 * Converts a {@link remixlab.dandelion.geom.Mat} to a PMatrix3D.
	 */
	public static PMatrix3D toPMatrix(Mat m) {
		float[] a = m.getTransposed(new float[16]);
		return new PMatrix3D(a[0], a[1], a[2], a[3],
				a[4], a[5], a[6], a[7],
				a[8], a[9], a[10], a[11],
				a[12], a[13], a[14], a[15]);
	}

	/**
	 * Converts a PMatrix3D to a {@link remixlab.dandelion.geom.Mat}.
	 */
	public static Mat toMat(PMatrix3D m) {
		return new Mat(m.get(new float[16]), true);
	}

	/**
	 * Converts a PMatrix2D to a {@link remixlab.dandelion.geom.Mat}.
	 */
	public static Mat toMat(PMatrix2D m) {
		return toMat(new PMatrix3D(m));
	}

	/**
	 * Converts a {@link remixlab.dandelion.geom.Mat} to a PMatrix2D.
	 */
	public static PMatrix2D toPMatrix2D(Mat m) {
		float[] a = m.getTransposed(new float[16]);
		return new PMatrix2D(a[0], a[1], a[3],
				a[4], a[5], a[7]);
	}

	// firstly, of course, dirty things that I used to love :P

	// DEFAULT MOTION-AGENT

	/**
	 * Enables Proscene mouse handling through the {@link #mouseAgent()}.
	 * 
	 * @see #isMotionAgentEnabled()
	 * @see #disableMotionAgent()
	 * @see #enableKeyboardAgent()
	 */
	@Override
	public void enableMotionAgent() {
		if (!inputHandler().isAgentRegistered(motionAgent())) {
			inputHandler().registerAgent(motionAgent());
			parent.registerMethod("mouseEvent", motionAgent());
		}
	}

	/**
	 * Disables the default mouse agent and returns it.
	 * 
	 * @see #isMotionAgentEnabled()
	 * @see #enableMotionAgent()
	 * @see #enableKeyboardAgent()
	 */
	@Override
	public ActionWheeledBiMotionAgent<?> disableMotionAgent() {
		if (inputHandler().isAgentRegistered(motionAgent())) {
			parent.unregisterMethod("mouseEvent", motionAgent());
			return (ActionWheeledBiMotionAgent<?>) inputHandler().unregisterAgent(motionAgent());
		}
		return motionAgent();
	}

	// MOUSE

	/**
	 * Returns the default mouse agent handling Processing mouse events. Simply returns a ProsceneMouse cast of the
	 * {@link #motionAgent()}.
	 * <p>
	 * The use of {@link #motionAgent()} is preferable and encouraged since it's more general and platform independent,
	 * i.e., it returns a "mouse agent" for the proscene desktop version or a "touch agent" for the android version.
	 * <p>
	 * If you plan to customize your mouse you can either use this method or one of the multiple high-level methods
	 * provided (recommended and simpler way), such as {@code setMouseAsArcball}, {@code setMouseAsFirstPerson()},
	 * {@code setMouseAsThirdPerson()}, {@code setMouseButtonBinding}, {@code setMouseClickBinding},
	 * {@code setMouseWheelBinding}, etc. All those methods actually wrap the mouse agent to achieve their functionality.
	 * 
	 * @see #keyboardAgent()
	 */
	public MouseAgent mouseAgent() {
		if (platform() == Platform.PROCESSING_ANDROID) {
			throw new RuntimeException("Proscene mouseAgent() is not available in Android mode");
		}
		return (MouseAgent) motionAgent();
	}

	/**
	 * Use {@link #enableMotionAgent()} instead.
	 */
	@Deprecated
	public void enableMouseAgent() {
		if (platform() == Platform.PROCESSING_ANDROID) {
			AbstractScene.showPlatformVariationWarning("enableMouseAgent", platform());
			return;
		}
		enableMotionAgent();
	}

	/**
	 * Use {@link #disableMotionAgent()} instead.
	 */
	@Deprecated
	public WheeledMouseAgent disableMouseAgent() {
		return (WheeledMouseAgent) disableMotionAgent();
	}

	/**
	 * Use {@link #isMotionAgentEnabled()} instead.
	 */
	@Deprecated
	public boolean isMouseAgentEnabled() {
		if (platform() == Platform.PROCESSING_ANDROID) {
			AbstractScene.showPlatformVariationWarning("isMouseAgentEnabled", platform());
			return false;
		}
		return isMotionAgentEnabled();
	}

	/**
	 * Set mouse bindings as 'arcball':
	 * <p>
	 * 1. <b>InteractiveFrame bindings</b><br>
	 * Left button -> ROTATE<br>
	 * Center button -> SCALE<br>
	 * Right button -> TRANSLATE<br>
	 * Shift + Center button -> SCREEN_TRANSLATE<br>
	 * Shift + Right button -> SCREEN_ROTATE<br>
	 * <p>
	 * 2. <b>InteractiveEyeFrame bindings</b><br>
	 * Left button -> ROTATE<br>
	 * Center button -> ZOOM<br>
	 * Right button -> TRANSLATE<br>
	 * Shift + Left button -> ZOOM_ON_REGION<br>
	 * Shift + Center button -> SCREEN_TRANSLATE<br>
	 * Shift + Right button -> SCREEN_ROTATE.
	 * <p>
	 * Also set the following (common) bindings are:
	 * <p>
	 * 2 left clicks -> ALIGN_FRAME<br>
	 * 2right clicks -> CENTER_FRAME<br>
	 * Wheel in 2D -> SCALE both, InteractiveFrame and InteractiveEyeFrame<br>
	 * Wheel in 3D -> SCALE InteractiveFrame, and ZOOM InteractiveEyeFrame<br>
	 * 
	 * @see #setMouseAsFirstPerson()
	 * @see #setMouseAsThirdPerson()
	 */
	public void setMouseAsArcball() {
		mouseAgent().setAsArcball();
	}

	/**
	 * Set mouse bindings as 'first-person':
	 * <p>
	 * 1. <b>InteractiveFrame bindings</b><br>
	 * Left button -> ROTATE<br>
	 * Center button -> SCALE<br>
	 * Right button -> TRANSLATE<br>
	 * Shift + Center button -> SCREEN_TRANSLATE<br>
	 * Shift + Right button -> SCREEN_ROTATE<br>
	 * <p>
	 * 2. <b>InteractiveEyeFrame bindings</b><br>
	 * Left button -> MOVE_FORWARD<br>
	 * Center button -> LOOK_AROUND<br>
	 * Right button -> MOVE_BACKWARD<br>
	 * Shift + Left button -> ROLL<br>
	 * Shift + Center button -> DRIVE<br>
	 * Ctrl + Wheel -> ROLL<br>
	 * Shift + Wheel -> DRIVE<br>
	 * <p>
	 * Also set the following (common) bindings are:
	 * <p>
	 * 2 left clicks -> ALIGN_FRAME<br>
	 * 2right clicks -> CENTER_FRAME<br>
	 * Wheel in 2D -> SCALE both, InteractiveFrame and InteractiveEyeFrame<br>
	 * Wheel in 3D -> SCALE InteractiveFrame, and ZOOM InteractiveEyeFrame<br>
	 * 
	 * @see #setMouseAsArcball()
	 * @see #setMouseAsThirdPerson()
	 */
	public void setMouseAsFirstPerson() {
		mouseAgent().setAsFirstPerson();
	}

	/**
	 * Set mouse bindings as third-person:
	 * <p>
	 * Left button -> MOVE_FORWARD<br>
	 * Center button -> LOOK_AROUND<br>
	 * Right button -> MOVE_BACKWARD<br>
	 * Shift + Left button -> ROLL<br>
	 * Shift + Center button -> DRIVE<br>
	 * <p>
	 * Also set the following (common) bindings are:
	 * <p>
	 * 2 left clicks -> ALIGN_FRAME<br>
	 * 2right clicks -> CENTER_FRAME<br>
	 * Wheel in 2D -> SCALE both, InteractiveFrame and InteractiveEyeFrame<br>
	 * Wheel in 3D -> SCALE InteractiveFrame, and ZOOM InteractiveEyeFrame<br>
	 * 
	 * @see #setMouseAsArcball()
	 * @see #setMouseAsFirstPerson()
	 */
	public void setMouseAsThirdPerson() {
		mouseAgent().setAsThirdPerson();
	}

	/**
	 * Binds the mask-button mouse shortcut to the (DOF2) dandelion action to be performed by the given {@code target}
	 * (EYE or FRAME).
	 */
	public void setMouseButtonBinding(Target target, int mask, int button, DOF2Action action) {
		mouseAgent().setButtonBinding(target, mask, button, action);
	}

	/**
	 * Binds the button mouse shortcut to the (DOF2) dandelion action to be performed by the given {@code target} (EYE or
	 * FRAME).
	 */
	public void setMouseButtonBinding(Target target, int button, DOF2Action action) {
		mouseAgent().setButtonBinding(target, button, action);
	}

	/**
	 * Removes the mask-button mouse shortcut binding from the given {@code target} (EYE or FRAME).
	 */
	public void removeMouseButtonBinding(Target target, int mask, int button) {
		mouseAgent().removeButtonBinding(target, mask, button);
	}

	/**
	 * Removes the button mouse shortcut binding from the given {@code target} (EYE or FRAME).
	 */
	public void removeMouseButtonBinding(Target target, int button) {
		mouseAgent().removeButtonBinding(target, button);
	}

	/**
	 * Returns {@code true} if the mask-button mouse shortcut is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean hasMouseButtonBinding(Target target, int mask, int button) {
		return mouseAgent().hasButtonBinding(target, mask, button);
	}

	/**
	 * Use {@link #hasMouseButtonBinding(remixlab.dandelion.core.Constants.Target, int, int)} instead.
	 * 
	 * @deprecated Please refrain from using this method, it will be removed from future releases.
	 */
	@Deprecated
	public boolean isMouseButtonBindingInUse(Target target, int mask, int button) {
		return hasMouseButtonBinding(target, mask, button);
	}

	/**
	 * Returns {@code true} if the button mouse shortcut is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean hasMouseButtonBinding(Target target, int button) {
		return mouseAgent().hasButtonBinding(target, button);
	}

	/**
	 * Use {@link #hasMouseButtonBinding(remixlab.dandelion.core.Constants.Target, int)} instead.
	 * 
	 * @deprecated Please refrain from using this method, it will be removed from future releases.
	 */
	@Deprecated
	public boolean isMouseButtonBindingInUse(Target target, int button) {
		return hasMouseButtonBinding(target, button);
	}

	/**
	 * Returns {@code true} if the mouse action is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean isMouseButtonActionBound(Target target, DOF2Action action) {
		return mouseAgent().isButtonActionBound(target, action);
	}

	/**
	 * Returns the (DOF2) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to the
	 * given mask-button mouse shortcut. Returns {@code null} if no action is bound to the given shortcut.
	 */
	public DOF2Action mouseButtonAction(Target target, int mask, int button) {
		return mouseAgent().buttonAction(target, mask, button);
	}

	/**
	 * Returns the (DOF2) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to the
	 * given button mouse shortcut. Returns {@code null} if no action is bound to the given shortcut.
	 */
	public DOF2Action mouseButtonAction(Target target, int button) {
		return mouseAgent().buttonAction(target, button);
	}

	// wheel here

	/**
	 * Use setMouseWheelBinding(Target, int, DOF1Action) instead.
	 * 
	 * @deprecated Please refrain from using this method, it will be removed from future releases.
	 */
	@Deprecated
	public void setMouseWheelBinding(Target target, int mask, WheelAction action) {
		DOF1Action dof1Action = null;
		if (action != null)
			switch (action) {
			case CUSTOM:
				dof1Action = DOF1Action.CUSTOM;
				break;
			case ROTATE_X:
				dof1Action = DOF1Action.ROTATE_X;
				break;
			case ROTATE_Y:
				dof1Action = DOF1Action.ROTATE_Y;
				break;
			case ROTATE_Z:
				dof1Action = DOF1Action.ROTATE_Z;
				break;
			case SCALE:
				dof1Action = DOF1Action.SCALE;
				break;
			case TRANSLATE_X:
				dof1Action = DOF1Action.TRANSLATE_X;
				break;
			case TRANSLATE_Y:
				dof1Action = DOF1Action.TRANSLATE_Y;
				break;
			case TRANSLATE_Z:
				dof1Action = DOF1Action.TRANSLATE_Z;
				break;
			case ZOOM:
				dof1Action = DOF1Action.ZOOM;
				break;
			case ZOOM_ON_ANCHOR:
				dof1Action = DOF1Action.ZOOM_ON_ANCHOR;
				break;
			}
		setMouseWheelBinding(target, mask, dof1Action);
	}

	/**
	 * Use setMouseWheelBinding(Target, DOF1Action) instead.
	 * 
	 * @deprecated Please refrain from using this method, it will be removed from future releases.
	 */
	@Deprecated
	public void setMouseWheelBinding(Target target, WheelAction action) {
		DOF1Action dof1Action = null;
		if (action != null)
			switch (action) {
			case CUSTOM:
				dof1Action = DOF1Action.CUSTOM;
				break;
			case ROTATE_X:
				dof1Action = DOF1Action.ROTATE_X;
				break;
			case ROTATE_Y:
				dof1Action = DOF1Action.ROTATE_Y;
				break;
			case ROTATE_Z:
				dof1Action = DOF1Action.ROTATE_Z;
				break;
			case SCALE:
				dof1Action = DOF1Action.SCALE;
				break;
			case TRANSLATE_X:
				dof1Action = DOF1Action.TRANSLATE_X;
				break;
			case TRANSLATE_Y:
				dof1Action = DOF1Action.TRANSLATE_Y;
				break;
			case TRANSLATE_Z:
				dof1Action = DOF1Action.TRANSLATE_Z;
				break;
			case ZOOM:
				dof1Action = DOF1Action.ZOOM;
				break;
			case ZOOM_ON_ANCHOR:
				dof1Action = DOF1Action.ZOOM_ON_ANCHOR;
				break;
			}
		setMouseWheelBinding(target, dof1Action);
	}

	/**
	 * Binds the mask-wheel shortcut to the (DOF1) dandelion action to be performed by the given {@code target} (EYE or
	 * FRAME).
	 */
	public void setMouseWheelBinding(Target target, int mask, DOF1Action action) {
		mouseAgent().setWheelBinding(target, mask, action);
	}

	/**
	 * Binds the wheel to the (DOF1) dandelion action to be performed by the given {@code target} (EYE or FRAME).
	 */
	public void setMouseWheelBinding(Target target, DOF1Action action) {
		mouseAgent().setWheelBinding(target, action);
	}

	/**
	 * Removes the mask-wheel shortcut binding from the given {@code target} (EYE or FRAME).
	 */
	public void removeMouseWheelBinding(Target target, int mask) {
		mouseAgent().removeWheelBinding(target, mask);
	}

	/**
	 * Removes the wheel binding from the given {@code target} (EYE or FRAME).
	 */
	public void removeMouseWheelBinding(Target target) {
		mouseAgent().removeWheelBinding(target);
	}

	/**
	 * Returns {@code true} if the mask-wheel shortcut is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean hasMouseWheelBinding(Target target, int mask) {
		return mouseAgent().hasWheelBinding(target, mask);
	}

	/**
	 * Use {@link #hasMouseWheelBinding(remixlab.dandelion.core.Constants.Target, int)} instead.
	 * 
	 * @deprecated Please refrain from using this method, it will be removed from future releases.
	 */
	@Deprecated
	public boolean isMouseWheelBindingInUse(Target target, int mask) {
		return hasMouseWheelBinding(target, mask);
	}

	/**
	 * Returns {@code true} if the wheel is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean hasMouseWheelBinding(Target target) {
		return mouseAgent().hasWheelBinding(target);
	}

	/**
	 * Use {@link #hasMouseWheelBinding(remixlab.dandelion.core.Constants.Target)} instead.
	 * 
	 * @deprecated Please refrain from using this method, it will be removed from future releases.
	 */
	@Deprecated
	public boolean isMouseWheelBindingInUse(Target target) {
		return hasMouseWheelBinding(target);
	}

	/**
	 * Returns {@code true} if the mouse wheel action is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean isMouseWheelActionBound(Target target, DOF1Action action) {
		return mouseAgent().isWheelActionBound(target, action);
	}

	/**
	 * Returns the (DOF1) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to the
	 * given mask-wheel shortcut. Returns {@code null} if no action is bound to the given shortcut.
	 */
	public DOF1Action mouseWheelAction(Target target, int mask, DOF1Action action) {
		return mouseAgent().wheelAction(target, mask, action);
	}

	/**
	 * Returns the (DOF1) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to the
	 * given wheel shortcut. Returns {@code null} if no action is bound to the given shortcut.
	 */
	public DOF1Action mouseWheelAction(Target target, DOF1Action action) {
		return mouseAgent().wheelAction(target, action);
	}

	// mouse click

	/**
	 * Binds the mask-button-ncs (number-of-clicks) click-shortcut to the (click) dandelion action to be performed by the
	 * given {@code target} (EYE or FRAME).
	 */
	public void setMouseClickBinding(Target target, int mask, int button, int ncs, ClickAction action) {
		mouseAgent().setClickBinding(target, mask, button, ncs, action);
	}

	/**
	 * Binds the button-ncs (number-of-clicks) click-shortcut to the (click) dandelion action to be performed by the given
	 * {@code target} (EYE or FRAME).
	 */
	public void setMouseClickBinding(Target target, int button, int ncs, ClickAction action) {
		mouseAgent().setClickBinding(target, button, ncs, action);
	}

	/**
	 * Binds the single-clicked button shortcut to the (click) dandelion action to be performed by the given
	 * {@code target} (EYE or FRAME).
	 */
	public void setMouseClickBinding(Target target, int button, ClickAction action) {
		mouseAgent().setClickBinding(target, button, action);
	}

	/**
	 * Removes the mask-button-ncs (number-of-clicks) click-shortcut binding from the
	 * {@link remixlab.dandelion.core.InteractiveEyeFrame} (if {@code eye} is {@code true}) or from the
	 * {@link remixlab.dandelion.core.InteractiveFrame} (if {@code eye} is {@code false}).
	 */
	public void removeMouseClickBinding(Target target, int mask, int button, int ncs) {
		mouseAgent().removeClickBinding(target, mask, button, ncs);
	}

	/**
	 * Removes the button-ncs (number-of-clicks) click-shortcut binding from the given {@code target} (EYE or FRAME).
	 */
	public void removeMouseClickBinding(Target target, int button, int ncs) {
		mouseAgent().removeClickBinding(target, button, ncs);
	}

	/**
	 * Removes the single-clicked button shortcut binding from the given {@code target} (EYE or FRAME).
	 */
	public void removeMouseClickBinding(Target target, int button) {
		mouseAgent().removeClickBinding(target, button);
	}

	/**
	 * Returns {@code true} if the mask-button-ncs (number-of-clicks) click-shortcut is bound to the given {@code target}
	 * (EYE or FRAME).
	 */
	public boolean hasMouseClickBinding(Target target, int mask, int button, int ncs) {
		return mouseAgent().hasClickBinding(target, mask, button, ncs);
	}

	/**
	 * Use {@link #hasMouseClickBinding(remixlab.dandelion.core.Constants.Target, int, int, int)} instead.
	 * 
	 * @deprecated Please refrain from using this method, it will be removed from future releases.
	 */
	@Deprecated
	public boolean isMouseClickBindingInUse(Target target, int mask, int button, int ncs) {
		return hasMouseClickBinding(target, mask, button, ncs);
	}

	/**
	 * Returns {@code true} if the button-ncs (number-of-clicks) click-shortcut is bound to the given {@code target} (EYE
	 * or FRAME).
	 */
	public boolean hasMouseClickBinding(Target target, int button, int ncs) {
		return mouseAgent().hasClickBinding(target, button, ncs);
	}

	/**
	 * Use {@link #hasMouseClickBinding(remixlab.dandelion.core.Constants.Target, int, int)} instead.
	 * 
	 * @deprecated Please refrain from using this method, it will be removed from future releases.
	 */
	@Deprecated
	public boolean isMouseClickBindingInUse(Target target, int button, int ncs) {
		return hasMouseClickBinding(target, button, ncs);
	}

	/**
	 * Returns {@code true} if the single-clicked button shortcut is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean hasMouseClickBinding(Target target, int button) {
		return mouseAgent().hasClickBinding(target, button);
	}

	/**
	 * Use {@link #hasMouseClickBinding(remixlab.dandelion.core.Constants.Target, int)} instead.
	 * 
	 * @deprecated Please refrain from using this method, it will be removed from future releases.
	 */
	@Deprecated
	public boolean isMouseClickBindingInUse(Target target, int button) {
		return hasMouseClickBinding(target, button);
	}

	/**
	 * Returns {@code true} if the mouse click action is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean isMouseClickActionBound(Target target, ClickAction action) {
		return mouseAgent().isClickActionBound(target, action);
	}

	/**
	 * Returns the (click) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to
	 * the given mask-button-ncs (number-of-clicks) click-shortcut. Returns {@code null} if no action is bound to the
	 * given shortcut.
	 */
	public ClickAction mouseClickAction(Target target, int mask, int button, int ncs) {
		return mouseAgent().clickAction(target, mask, button, ncs);
	}

	/**
	 * Returns the (click) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to
	 * the given button-ncs (number-of-clicks) click-shortcut. Returns {@code null} if no action is bound to the given
	 * shortcut.
	 */
	public ClickAction mouseClickAction(Target target, int button, int ncs) {
		return mouseAgent().clickAction(target, button, ncs);
	}

	/**
	 * Returns the (click) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to
	 * the given single-clicked button shortcut. Returns {@code null} if no action is bound to the given shortcut.
	 */
	public ClickAction mouseClickAction(Target target, int button) {
		return mouseAgent().clickAction(target, button);
	}

	// KEYBOARD

	/**
	 * Restores the default keyboard shortcuts:
	 * <p>
	 * {@code 'a' -> KeyboardAction.TOGGLE_AXES_VISUAL_HINT}<br>
	 * {@code 'f' -> KeyboardAction.TOGGLE_FRAME_VISUAL_HINT}<br>
	 * {@code 'g' -> KeyboardAction.TOGGLE_GRID_VISUAL_HINT}<br>
	 * {@code 'm' -> KeyboardAction.TOGGLE_ANIMATION}<br>
	 * {@code 'e' -> KeyboardAction.TOGGLE_CAMERA_TYPE}<br>
	 * {@code 'h' -> KeyboardAction.DISPLAY_INFO}<br>
	 * {@code 'r' -> KeyboardAction.TOGGLE_PATHS_VISUAL_HINT}<br>
	 * {@code 's' -> KeyboardAction.INTERPOLATE_TO_FIT}<br>
	 * {@code 'S' -> KeyboardAction.SHOW_ALL}<br>
	 * {@code left_arrow -> KeyboardAction.MOVE_LEFT}<br>
	 * {@code right_arrow -> KeyboardAction.MOVE_RIGHT}<br>
	 * {@code up_arrow -> KeyboardAction.MOVE_UP}<br>
	 * {@code down_arrow -> KeyboardAction.MOVE_DOWN	}<br>
	 * {@code 'CTRL' + '1' -> KeyboardAction.ADD_KEYFRAME_TO_PATH_1}<br>
	 * {@code 'ALT' + '1' -> KeyboardAction.DELETE_PATH_1}<br>
	 * {@code '1' -> KeyboardAction.PLAY_PATH_1}<br>
	 * {@code 'CTRL' + '2' -> KeyboardAction.ADD_KEYFRAME_TO_PATH_2}<br>
	 * {@code 'ALT' + '2' -> KeyboardAction.DELETE_PATH_2}<br>
	 * {@code '2' -> KeyboardAction.PLAY_PATH_2}<br>
	 * {@code 'CTRL' + '3' -> KeyboardAction.ADD_KEYFRAME_TO_PATH_3}<br>
	 * {@code 'ALT' + '3' -> KeyboardAction.DELETE_PATH_3}<br>
	 * {@code '3' -> KeyboardAction.PLAY_PATH_3}<br>
	 * 
	 * @see remixlab.dandelion.agent.KeyboardAgent#setDefaultShortcuts()
	 * @see #setMouseAsArcball()
	 * @see #setMouseAsFirstPerson()
	 * @see #setMouseAsThirdPerson()
	 */
	public void setDefaultKeyboardShortcuts() {
		keyboardAgent().setDefaultShortcuts();
	}

	/**
	 * Set the virtual-key to play path. Defaults are java.awt.event.KeyEvent.VK_1, java.awt.event.KeyEvent.VK_2 and
	 * java.awt.event.KeyEvent.VK_3 which will play paths 1, 2, 3, resp.
	 */
	public void setKeyCodeToPlayPath(int code, int path) {
		keyboardAgent().setKeyCodeToPlayPath(code, path);
	}

	/**
	 * Binds the key shortcut to the (Keyboard) dandelion action.
	 */
	public void setKeyboardShortcut(Character key, KeyboardAction action) {
		keyboardAgent().setShortcut(key, action);
	}

	/**
	 * Binds the mask-vKey (virtual key) shortcut to the (Keyboard) dandelion action.
	 */
	public void setKeyboardShortcut(int mask, int vKey, KeyboardAction action) {
		keyboardAgent().setShortcut(mask, vKey, action);
	}

	/**
	 * Removes key shortcut binding (if present).
	 */
	public void removeKeyboardShortcut(Character key) {
		keyboardAgent().removeShortcut(key);
	}

	/**
	 * Removes mask-vKey (virtual key) shortcut binding (if present).
	 */
	public void removeKeyboardShortcut(int mask, int vKey) {
		keyboardAgent().removeShortcut(mask, vKey);
	}

	/**
	 * Returns {@code true} if the key shortcut is bound to a (Keyboard) dandelion action.
	 */
	public boolean hasKeyboardShortcut(Character key) {
		return keyboardAgent().hasShortcut(key);
	}

	/**
	 * Use {@link #hasKeyboardShortcut(Character)} instead.
	 * 
	 * @deprecated Please refrain from using this method, it will be removed from future releases.
	 */
	@Deprecated
	public boolean isKeyboardShortcutInUse(Character key) {
		return hasKeyboardShortcut(key);
	}

	/**
	 * Returns {@code true} if the mask-vKey (virtual key) shortcut is bound to a (Keyboard) dandelion action.
	 */
	public boolean hasKeyboardShortcut(int mask, int vKey) {
		return keyboardAgent().hasShortcut(mask, vKey);
	}

	/**
	 * Returns {@code true} if the keyboard action is bound.
	 */
	public boolean isKeyboardActionBound(KeyboardAction action) {
		return keyboardAgent().isActionBound(action);
	}

	/**
	 * Use {@link #hasKeyboardShortcut(int, int)} instead.
	 * 
	 * @deprecated Please refrain from using this method, it will be removed from future releases.
	 */
	@Deprecated
	public boolean isKeyboardShortcutInUse(int mask, int vKey) {
		return hasKeyboardShortcut(mask, vKey);
	}

	/**
	 * Returns the (Keyboard) dandelion action that is bound to the given key shortcut. Returns {@code null} if no action
	 * is bound to the given shortcut.
	 */
	public KeyboardAction keyboardAction(Character key) {
		return keyboardAgent().action(key);
	}

	/**
	 * Returns the (Keyboard) dandelion action that is bound to the given mask-vKey (virtual key) shortcut. Returns
	 * {@code null} if no action is bound to the given shortcut.
	 */
	public KeyboardAction keyboardAction(int mask, int vKey) {
		return keyboardAgent().action(mask, vKey);
	}

	/**
	 * Enables Proscene keyboard handling through the {@link #keyboardAgent()}.
	 * 
	 * @see #isKeyboardAgentEnabled()
	 * @see #disableKeyboardAgent()
	 * @see #enableMotionAgent()
	 */
	@Override
	public void enableKeyboardAgent() {
		if (!inputHandler().isAgentRegistered(keyboardAgent())) {
			inputHandler().registerAgent(keyboardAgent());
			parent.registerMethod("keyEvent", keyboardAgent());
		}
	}

	/**
	 * Disables the default keyboard agent and returns it.
	 * 
	 * @see #isKeyboardAgentEnabled()
	 * @see #enableKeyboardAgent()
	 * @see #disableMotionAgent()
	 */
	@Override
	public KeyboardAgent disableKeyboardAgent() {
		if (inputHandler().isAgentRegistered(keyboardAgent())) {
			parent.unregisterMethod("keyEvent", keyboardAgent());
			return (KeyboardAgent) inputHandler().unregisterAgent(keyboardAgent());
		}
		return keyboardAgent();
	}

	// INFO

	@Override
	public String info() {
		String info = super.info();

		String l = Integer.toString(PApplet.LEFT) + "_BUTTON";
		String c = Integer.toString(PApplet.CENTER) + "_BUTTON";
		String r = Integer.toString(PApplet.RIGHT) + "_BUTTON";

		info = info.replace(l, "LEFT_BUTTON").replace(c, "CENTER_BUTTON").replace(r, "RIGHT_BUTTON");
		String keyboardtitle = keyboardAgent().name()
				+ " (key-codes are defined here: http://docs.oracle.com/javase/7/docs/api/constant-values.html)";
		info = info.replace(keyboardAgent().name(), keyboardtitle);

		String vk_1 = "virtual_key (" + Integer.toString(49) + ")";
		String vk_2 = "virtual_key (" + Integer.toString(50) + ")";
		String vk_3 = "virtual_key (" + Integer.toString(51) + ")";
		String vk_l = "virtual_key (" + Integer.toString(37) + ")";
		String vk_u = "virtual_key (" + Integer.toString(38) + ")";
		String vk_r = "virtual_key (" + Integer.toString(39) + ")";
		String vk_d = "virtual_key (" + Integer.toString(40) + ")";

		info = info.replace(vk_1, "VK_1").replace(vk_2, "VK_2").replace(vk_3, "VK_3")
				.replace(vk_l, "VK_LEFT").replace(vk_u, "VK_UP").replace(vk_r, "VK_RIGHT").replace(vk_d, "VK_DOWN");

		return info;
	}

	@Override
	public void displayInfo(boolean onConsole) {
		if (onConsole)
			System.out.println(info());
		else { // on applet
			pg().textFont(parent.createFont("Arial", 12));
			beginScreenDrawing();
			pg().fill(0, 255, 0);
			pg().textLeading(20);
			pg().text(info(), 10, 10, (pg().width - 20), (pg().height - 20));
			endScreenDrawing();
		}
	}

	// begin: GWT-incompatible
	// /*

	// TIMING

	@Override
	public void registerTimingTask(TimingTask task) {
		if (areTimersSeq())
			timingHandler().registerTask(task);
		else
			timingHandler().registerTask(task, new NonSeqTimer(this, task));
	}

	/**
	 * Sets all {@link #timingHandler()} timers as (single-threaded) {@link remixlab.fpstiming.SeqTimer}(s).
	 * 
	 * @see #setNonSeqTimers()
	 * @see #switchTimers()
	 * @see #areTimersSeq()
	 */
	public void setSeqTimers() {
		if (areTimersSeq())
			return;

		javaTiming = false;
		timingHandler().restoreTimers();
	}

	/**
	 * Sets all {@link #timingHandler()} timers as (multi-threaded) java.util.Timer(s).
	 * 
	 * @see #setSeqTimers()
	 * @see #switchTimers()
	 * @see #areTimersSeq()
	 */
	public void setNonSeqTimers() {
		if (!areTimersSeq())
			return;

		boolean isActive;

		for (TimingTask task : timingHandler().timerPool()) {
			long period = 0;
			boolean rOnce = false;
			isActive = task.isActive();
			if (isActive) {
				period = task.period();
				rOnce = task.timer().isSingleShot();
			}
			task.stop();
			task.setTimer(new NonSeqTimer(this, task));
			if (isActive) {
				if (rOnce)
					task.runOnce(period);
				else
					task.run(period);
			}
		}

		javaTiming = true;
		PApplet.println("java util timers set");
	}

	/**
	 * @return true, if timing is handling sequentially (i.e., all {@link #timingHandler()} timers are (single-threaded)
	 *         {@link remixlab.fpstiming.SeqTimer}(s)).
	 * 
	 * @see #setSeqTimers()
	 * @see #setNonSeqTimers()
	 * @see #switchTimers()
	 */
	public boolean areTimersSeq() {
		return !javaTiming;
	}

	/**
	 * If {@link #areTimersSeq()} calls {@link #setNonSeqTimers()}, otherwise call {@link #setSeqTimers()}.
	 */
	public void switchTimers() {
		if (areTimersSeq())
			setNonSeqTimers();
		else
			setSeqTimers();
	}

	// DRAW METHOD REG

	@Override
	protected boolean invokeDrawHandler() {
		// 3. Draw external registered method
		if (drawHandlerObject != null) {
			try {
				drawHandlerMethod.invoke(drawHandlerObject, new Object[] { this });
				return true;
			} catch (Exception e) {
				PApplet.println("Something went wrong when invoking your " + drawHandlerMethodName + " method");
				e.printStackTrace();
				return false;
			}
		}
		return false;
	}

	/**
	 * Attempt to add a 'draw' handler method to the Scene. The default event handler is a method that returns void and
	 * has one single Scene parameter.
	 * 
	 * @param obj
	 *          the object to handle the event
	 * @param methodName
	 *          the method to execute in the object handler class
	 * 
	 * @see #removeDrawHandler()
	 * @see #invokeDrawHandler()
	 */
	public void addDrawHandler(Object obj, String methodName) {
		try {
			drawHandlerMethod = obj.getClass().getMethod(methodName, new Class<?>[] { Scene.class });
			drawHandlerObject = obj;
			drawHandlerMethodName = methodName;
		} catch (Exception e) {
			PApplet.println("Something went wrong when registering your " + methodName + " method");
			e.printStackTrace();
		}
	}

	/**
	 * Unregisters the 'draw' handler method (if any has previously been added to the Scene).
	 * 
	 * @see #addDrawHandler(Object, String)
	 * @see #invokeDrawHandler()
	 */
	public void removeDrawHandler() {
		drawHandlerMethod = null;
		drawHandlerObject = null;
		drawHandlerMethodName = null;
	}

	/**
	 * Returns {@code true} if the user has registered a 'draw' handler method to the Scene and {@code false} otherwise.
	 * 
	 * @see #addDrawHandler(Object, String)
	 * @see #invokeDrawHandler()
	 */
	public boolean hasDrawHandler() {
		if (drawHandlerMethodName == null)
			return false;
		return true;
	}

	// ANIMATION METHOD REG

	@Override
	public boolean invokeAnimationHandler() {
		if (animateHandlerObject != null) {
			try {
				animateHandlerMethod.invoke(animateHandlerObject, new Object[] { this });
				return true;
			} catch (Exception e) {
				PApplet.println("Something went wrong when invoking your " + animateHandlerMethodName + " method");
				e.printStackTrace();
				return false;
			}
		}
		return false;
	}

	/**
	 * Attempt to add an 'animation' handler method to the Scene. The default event handler is a method that returns void
	 * and has one single Scene parameter.
	 * 
	 * @param obj
	 *          the object to handle the event
	 * @param methodName
	 *          the method to execute in the object handler class
	 * 
	 * @see #animate()
	 * @see #removeAnimationHandler()
	 */
	public void addAnimationHandler(Object obj, String methodName) {
		try {
			animateHandlerMethod = obj.getClass().getMethod(methodName, new Class<?>[] { Scene.class });
			animateHandlerObject = obj;
			animateHandlerMethodName = methodName;
		} catch (Exception e) {
			PApplet.println("Something went wrong when registering your " + methodName + " method");
			e.printStackTrace();
		}
	}

	/**
	 * Unregisters the 'animation' handler method (if any has previously been added to the Scene).
	 * 
	 * @see #addAnimationHandler(Object, String)
	 */
	public void removeAnimationHandler() {
		animateHandlerMethod = null;
		animateHandlerObject = null;
		animateHandlerMethodName = null;
	}

	/**
	 * Returns {@code true} if the user has registered an 'animation' handler method to the Scene and {@code false}
	 * otherwise.
	 * 
	 * @see #addAnimationHandler(Object, String)
	 * @see #removeAnimationHandler()
	 */
	public boolean hasAnimationHandler() {
		if (animateHandlerMethodName == null)
			return false;
		return true;
	}

	// OPENGL

	@Override
	public float pixelDepth(Point pixel) {
		if (pg().smooth)
			throw new RuntimeException("pixelDepth requires scene.pg().noSmooth()");
		PGraphicsOpenGL pggl;
		if (pg() instanceof PGraphicsOpenGL)
			pggl = (PGraphicsOpenGL) pg();
		else
			throw new RuntimeException("pg() is not instance of PGraphicsOpenGL");
		float[] depth = new float[1];
		PGL pgl = pggl.beginPGL();
		pgl.readPixels(pixel.x(), (camera().screenHeight() - pixel.y()), 1, 1, PGL.DEPTH_COMPONENT, PGL.FLOAT,
				FloatBuffer.wrap(depth));
		pggl.endPGL();
		return depth[0];
	}

	@Override
	public void disableDepthTest() {
		pg().hint(PApplet.DISABLE_DEPTH_TEST);
	}

	@Override
	public void enableDepthTest() {
		pg().hint(PApplet.ENABLE_DEPTH_TEST);
	}

	// end: GWT-incompatible
	// */

	// 3. Drawing methods

	/**
	 * Paint method which is called just before your {@code PApplet.draw()} method. Simply calls {@link #preDraw()}. This
	 * method is registered at the PApplet and hence you don't need to call it.
	 * <p>
	 * If {@link #isOffscreen()} does nothing.
	 * <p>
	 * If {@link #pg()} is resized then (re)sets the scene {@link #width()} and {@link #height()}, and calls
	 * {@link remixlab.dandelion.core.Eye#setScreenWidthAndHeight(int, int)}.
	 * 
	 * @see #draw()
	 * @see #preDraw()
	 * @see #postDraw()
	 * @see #beginDraw()
	 * @see #endDraw()
	 * @see #isOffscreen()
	 */
	public void pre() {
		if (isOffscreen())
			return;

		if ((width != pg().width) || (height != pg().height)) {
			width = pg().width;
			height = pg().height;
			eye().setScreenWidthAndHeight(width, height);
		}

		preDraw();
	}

	/**
	 * Paint method which is called just after your {@code PApplet.draw()} method. Simply calls {@link #postDraw()}. This
	 * method is registered at the PApplet and hence you don't need to call it.
	 * <p>
	 * If {@link #isOffscreen()} does nothing.
	 * 
	 * @see #pre()
	 * @see #preDraw()
	 * @see #postDraw()
	 * @see #beginDraw()
	 * @see #endDraw()
	 * @see #isOffscreen()
	 */
	public void draw() {
		if (isOffscreen())
			return;
		postDraw();
	}

	/**
	 * Only if the Scene {@link #isOffscreen()}. This method should be called just after the {@link #pg()} beginDraw()
	 * method. Simply calls {@link #preDraw()}.
	 * <p>
	 * If {@link #pg()} is resized then (re)sets the scene {@link #width()} and {@link #height()}, and calls
	 * {@link remixlab.dandelion.core.Eye#setScreenWidthAndHeight(int, int)}.
	 * 
	 * @see #draw()
	 * @see #preDraw()
	 * @see #postDraw()
	 * @see #pre()
	 * @see #endDraw()
	 * @see #isOffscreen()
	 */
	public void beginDraw() {
		if (isOffscreen()) {
			if (beginOffScreenDrawingCalls != 0)
				throw new RuntimeException("There should be exactly one beginDraw() call followed by a "
						+ "endDraw() and they cannot be nested. Check your implementation!");
			beginOffScreenDrawingCalls++;

			if ((width != pg().width) || (height != pg().height)) {
				width = pg().width;
				height = pg().height;
				eye().setScreenWidthAndHeight(width, height);
			}

			preDraw();
		}
	}

	/**
	 * Only if the Scene {@link #isOffscreen()}. This method should be called just before {@link #pg()} endDraw() method.
	 * Simply calls {@link #postDraw()}.
	 * 
	 * @see #draw()
	 * @see #preDraw()
	 * @see #postDraw()
	 * @see #beginDraw()
	 * @see #pre()
	 * @see #isOffscreen()
	 */
	public void endDraw() {
		beginOffScreenDrawingCalls--;

		if (beginOffScreenDrawingCalls != 0)
			throw new RuntimeException(
					"There should be exactly one beginDraw() call followed by a "
							+ "endDraw() and they cannot be nested. Check your implementation!");

		postDraw();
	}

	// SCREENDRAWING

	/**
	 * Need to override it because of this issue: https://github.com/remixlab/proscene/issues/1
	 */
	@Override
	public void beginScreenDrawing() {
		if (startCoordCalls != 0)
			throw new RuntimeException("There should be exactly one beginScreenDrawing() call followed by a "
					+ "endScreenDrawing() and they cannot be nested. Check your implementation!");

		startCoordCalls++;

		pg().hint(PApplet.DISABLE_OPTIMIZED_STROKE);// -> new line not present in AbstractScene.bS
		disableDepthTest();
		matrixHelper.beginScreenDrawing();
	}

	/**
	 * Need to override it because of this issue: https://github.com/remixlab/proscene/issues/1
	 */
	@Override
	public void endScreenDrawing() {
		startCoordCalls--;
		if (startCoordCalls != 0)
			throw new RuntimeException("There should be exactly one beginScreenDrawing() call followed by a "
					+ "endScreenDrawing() and they cannot be nested. Check your implementation!");

		matrixHelper.endScreenDrawing();
		enableDepthTest();
		pg().hint(PApplet.ENABLE_OPTIMIZED_STROKE);// -> new line not present in AbstractScene.bS
	}

	// DRAWING

	@Override
	public void drawCylinder(float w, float h) {
		if (is2D()) {
			AbstractScene.showDepthWarning("drawCylinder");
			return;
		}

		pg().pushStyle();
		float px, py;

		pg().beginShape(PApplet.QUAD_STRIP);
		for (float i = 0; i < 13; i++) {
			px = (float) Math.cos(PApplet.radians(i * 30)) * w;
			py = (float) Math.sin(PApplet.radians(i * 30)) * w;
			vertex(px, py, 0);
			vertex(px, py, h);
		}
		pg().endShape();

		pg().beginShape(PApplet.TRIANGLE_FAN);
		vertex(0, 0, 0);
		for (float i = 12; i > -1; i--) {
			px = (float) Math.cos(PApplet.radians(i * 30)) * w;
			py = (float) Math.sin(PApplet.radians(i * 30)) * w;
			vertex(px, py, 0);
		}
		pg().endShape();

		pg().beginShape(PApplet.TRIANGLE_FAN);
		vertex(0, 0, h);
		for (float i = 0; i < 13; i++) {
			px = (float) Math.cos(PApplet.radians(i * 30)) * w;
			py = (float) Math.sin(PApplet.radians(i * 30)) * w;
			vertex(px, py, h);
		}
		pg().endShape();
		pg().popStyle();
	}

	@Override
	public void drawHollowCylinder(int detail, float w, float h, Vec m, Vec n) {
		if (is2D()) {
			AbstractScene.showDepthWarning("drawHollowCylinder");
			return;
		}

		pg().pushStyle();
		// eqs taken from: http://en.wikipedia.org/wiki/Line-plane_intersection
		Vec pm0 = new Vec(0, 0, 0);
		Vec pn0 = new Vec(0, 0, h);
		Vec l0 = new Vec();
		Vec l = new Vec(0, 0, 1);
		Vec p = new Vec();
		float x, y, d;

		pg().noStroke();
		pg().beginShape(PApplet.QUAD_STRIP);

		for (float t = 0; t <= detail; t++) {
			x = w * PApplet.cos(t * PApplet.TWO_PI / detail);
			y = w * PApplet.sin(t * PApplet.TWO_PI / detail);
			l0.set(x, y, 0);

			d = (m.dot(Vec.subtract(pm0, l0))) / (l.dot(m));
			p = Vec.add(Vec.multiply(l, d), l0);
			vertex(p.x(), p.y(), p.z());

			l0.setZ(h);
			d = (n.dot(Vec.subtract(pn0, l0))) / (l.dot(n));
			p = Vec.add(Vec.multiply(l, d), l0);
			vertex(p.x(), p.y(), p.z());
		}
		pg().endShape();
		pg().popStyle();
	}

	@Override
	public void drawCone(int detail, float x, float y, float r, float h) {
		if (is2D()) {
			AbstractScene.showDepthWarning("drawCone");
			return;
		}
		pg().pushStyle();
		float unitConeX[] = new float[detail + 1];
		float unitConeY[] = new float[detail + 1];

		for (int i = 0; i <= detail; i++) {
			float a1 = PApplet.TWO_PI * i / detail;
			unitConeX[i] = r * (float) Math.cos(a1);
			unitConeY[i] = r * (float) Math.sin(a1);
		}

		pushModelView();
		translate(x, y);
		pg().beginShape(PApplet.TRIANGLE_FAN);
		vertex(0, 0, h);
		for (int i = 0; i <= detail; i++) {
			vertex(unitConeX[i], unitConeY[i], 0.0f);
		}
		pg().endShape();
		popModelView();
		pg().popStyle();
	}

	@Override
	public void drawCone(int detail, float x, float y, float r1, float r2, float h) {
		if (is2D()) {
			AbstractScene.showDepthWarning("drawCone");
			return;
		}
		pg().pushStyle();
		float firstCircleX[] = new float[detail + 1];
		float firstCircleY[] = new float[detail + 1];
		float secondCircleX[] = new float[detail + 1];
		float secondCircleY[] = new float[detail + 1];

		for (int i = 0; i <= detail; i++) {
			float a1 = PApplet.TWO_PI * i / detail;
			firstCircleX[i] = r1 * (float) Math.cos(a1);
			firstCircleY[i] = r1 * (float) Math.sin(a1);
			secondCircleX[i] = r2 * (float) Math.cos(a1);
			secondCircleY[i] = r2 * (float) Math.sin(a1);
		}

		pushModelView();
		translate(x, y);
		pg().beginShape(PApplet.QUAD_STRIP);
		for (int i = 0; i <= detail; i++) {
			vertex(firstCircleX[i], firstCircleY[i], 0);
			vertex(secondCircleX[i], secondCircleY[i], h);
		}
		pg().endShape();
		popModelView();
		pg().popStyle();
	}

	@Override
	public void drawAxes(float length) {
		pg().pushStyle();
		pg().colorMode(PApplet.RGB, 255);
		float charWidth = length / 40.0f;
		float charHeight = length / 30.0f;
		float charShift = 1.04f * length;

		pg().pushStyle();
		pg().beginShape(PApplet.LINES);
		pg().strokeWeight(2);
		if (is2D()) {
			// The X
			pg().stroke(200, 0, 0);
			vertex(charShift + charWidth, -charHeight);
			vertex(charShift - charWidth, charHeight);
			vertex(charShift - charWidth, -charHeight);
			vertex(charShift + charWidth, charHeight);

			// The Y
			charShift *= 1.02;
			pg().stroke(0, 200, 0);
			vertex(charWidth, charShift + (isRightHanded() ? charHeight : -charHeight));
			vertex(0.0f, charShift + 0.0f);
			vertex(-charWidth, charShift + (isRightHanded() ? charHeight : -charHeight));
			vertex(0.0f, charShift + 0.0f);
			vertex(0.0f, charShift + 0.0f);
			vertex(0.0f, charShift + -(isRightHanded() ? charHeight : -charHeight));
		}
		else {
			// The X
			pg().stroke(200, 0, 0);
			vertex(charShift, charWidth, -charHeight);
			vertex(charShift, -charWidth, charHeight);
			vertex(charShift, -charWidth, -charHeight);
			vertex(charShift, charWidth, charHeight);
			// The Y
			pg().stroke(0, 200, 0);
			vertex(charWidth, charShift, (isLeftHanded() ? charHeight : -charHeight));
			vertex(0.0f, charShift, 0.0f);
			vertex(-charWidth, charShift, (isLeftHanded() ? charHeight : -charHeight));
			vertex(0.0f, charShift, 0.0f);
			vertex(0.0f, charShift, 0.0f);
			vertex(0.0f, charShift, -(isLeftHanded() ? charHeight : -charHeight));
			// The Z
			pg().stroke(0, 100, 200);
			vertex(-charWidth, isRightHanded() ? charHeight : -charHeight, charShift);
			vertex(charWidth, isRightHanded() ? charHeight : -charHeight, charShift);
			vertex(charWidth, isRightHanded() ? charHeight : -charHeight, charShift);
			vertex(-charWidth, isRightHanded() ? -charHeight : charHeight, charShift);
			vertex(-charWidth, isRightHanded() ? -charHeight : charHeight, charShift);
			vertex(charWidth, isRightHanded() ? -charHeight : charHeight, charShift);
		}
		pg().endShape();
		pg().popStyle();

		// X Axis
		pg().stroke(200, 0, 0);
		line(0, 0, 0, length, 0, 0);
		// Y Axis
		pg().stroke(0, 200, 0);
		line(0, 0, 0, 0, length, 0);

		// Z Axis
		if (is3D()) {
			pg().stroke(0, 100, 200);
			line(0, 0, 0, 0, 0, length);
		}
		pg().popStyle();
	}

	@Override
	public void drawGrid(float size, int nbSubdivisions) {
		pg().pushStyle();
		pg().beginShape(LINES);
		for (int i = 0; i <= nbSubdivisions; ++i) {
			final float pos = size * (2.0f * i / nbSubdivisions - 1.0f);
			vertex(pos, -size);
			vertex(pos, +size);
			vertex(-size, pos);
			vertex(size, pos);
		}
		pg().endShape();
		pg().popStyle();
	}

	@Override
	public void drawDottedGrid(float size, int nbSubdivisions) {
		pg().pushStyle();
		float posi, posj;
		pg().beginShape(POINTS);
		for (int i = 0; i <= nbSubdivisions; ++i) {
			posi = size * (2.0f * i / nbSubdivisions - 1.0f);
			for (int j = 0; j <= nbSubdivisions; ++j) {
				posj = size * (2.0f * j / nbSubdivisions - 1.0f);
				vertex(posi, posj);
			}
		}
		pg().endShape();
		int internalSub = 5;
		int subSubdivisions = nbSubdivisions * internalSub;
		float currentWeight = pg().strokeWeight;
		pg().colorMode(HSB, 255);
		float hue = pg().hue(pg().strokeColor);
		float saturation = pg().saturation(pg().strokeColor);
		float brightness = pg().brightness(pg().strokeColor);
		pg().stroke(hue, saturation, brightness * 10f / 17f);
		pg().strokeWeight(currentWeight / 2);
		pg().beginShape(POINTS);
		for (int i = 0; i <= subSubdivisions; ++i) {
			posi = size * (2.0f * i / subSubdivisions - 1.0f);
			for (int j = 0; j <= subSubdivisions; ++j) {
				posj = size * (2.0f * j / subSubdivisions - 1.0f);
				if (((i % internalSub) != 0) || ((j % internalSub) != 0))
					vertex(posi, posj);
			}
		}
		pg().endShape();
		pg().popStyle();
	}

	@Override
	public void drawEye(Eye eye, float scale) {
		pg().pushStyle();
		// boolean drawFarPlane = true;
		// int farIndex = drawFarPlane ? 1 : 0;
		int farIndex = is3D() ? 1 : 0;
		boolean ortho = false;
		if (is3D())
			if (((Camera) eye).type() == Camera.Type.ORTHOGRAPHIC)
				ortho = true;
		pushModelView();
		// applyMatrix(camera.frame().worldMatrix());
		// same as the previous line, but maybe more efficient

		// Frame tmpFrame = new Frame(is3D());
		// tmpFrame.fromMatrix(eye.frame().worldMatrix());
		// applyTransformation(tmpFrame);
		// same as above but easier
		// scene().applyTransformation(camera.frame());

		// fails due to scaling!

		// take into account the whole hierarchy:
		if (is2D()) {
			// applyWorldTransformation(eye.frame());
			translate(eye.frame().position().vec[0], eye.frame().position().vec[1]);
			rotate(eye.frame().orientation().angle());
		} else {
			translate(eye.frame().position().vec[0], eye.frame().position().vec[1], eye.frame().position().vec[2]);
			rotate(eye.frame().orientation().angle(), ((Quat) eye.frame().orientation()).axis().vec[0], ((Quat) eye.frame()
					.orientation()).axis().vec[1], ((Quat) eye.frame().orientation()).axis().vec[2]);
		}

		// 0 is the upper left coordinates of the near corner, 1 for the far one
		Vec[] points = new Vec[2];
		points[0] = new Vec();
		points[1] = new Vec();

		if (is2D() || ortho) {
			float[] wh = eye.getBoundaryWidthHeight();
			points[0].setX(scale * wh[0]);
			points[1].setX(scale * wh[0]);
			points[0].setY(scale * wh[1]);
			points[1].setY(scale * wh[1]);
		}

		if (is3D()) {
			points[0].setZ(scale * ((Camera) eye).zNear());
			points[1].setZ(scale * ((Camera) eye).zFar());

			if (((Camera) eye).type() == Camera.Type.PERSPECTIVE) {
				points[0].setY(points[0].z() * PApplet.tan(((Camera) eye).fieldOfView() / 2.0f));
				points[0].setX(points[0].y() * ((Camera) eye).aspectRatio());
				float ratio = points[1].z() / points[0].z();
				points[1].setY(ratio * points[0].y());
				points[1].setX(ratio * points[0].x());
			}

			// Frustum lines
			switch (((Camera) eye).type()) {
			case PERSPECTIVE: {
				pg().beginShape(PApplet.LINES);
				vertex(0.0f, 0.0f, 0.0f);
				vertex(points[farIndex].x(), points[farIndex].y(), -points[farIndex].z());
				vertex(0.0f, 0.0f, 0.0f);
				vertex(-points[farIndex].x(), points[farIndex].y(), -points[farIndex].z());
				vertex(0.0f, 0.0f, 0.0f);
				vertex(-points[farIndex].x(), -points[farIndex].y(), -points[farIndex].z());
				vertex(0.0f, 0.0f, 0.0f);
				vertex(points[farIndex].x(), -points[farIndex].y(), -points[farIndex].z());
				pg().endShape();
				break;
			}
			case ORTHOGRAPHIC: {
				// if (drawFarPlane) {
				pg().beginShape(PApplet.LINES);
				vertex(points[0].x(), points[0].y(), -points[0].z());
				vertex(points[1].x(), points[1].y(), -points[1].z());
				vertex(-points[0].x(), points[0].y(), -points[0].z());
				vertex(-points[1].x(), points[1].y(), -points[1].z());
				vertex(-points[0].x(), -points[0].y(), -points[0].z());
				vertex(-points[1].x(), -points[1].y(), -points[1].z());
				vertex(points[0].x(), -points[0].y(), -points[0].z());
				vertex(points[1].x(), -points[1].y(), -points[1].z());
				pg().endShape();
				// }
				break;
			}
			}
		}

		// Near and (optionally) far plane(s)
		pg().noStroke();
		pg().beginShape(PApplet.QUADS);
		for (int i = farIndex; i >= 0; --i) {
			pg().normal(0.0f, 0.0f, (i == 0) ? 1.0f : -1.0f);
			vertex(points[i].x(), points[i].y(), -points[i].z());
			vertex(-points[i].x(), points[i].y(), -points[i].z());
			vertex(-points[i].x(), -points[i].y(), -points[i].z());
			vertex(points[i].x(), -points[i].y(), -points[i].z());
		}
		pg().endShape();

		// Up arrow
		float arrowHeight = 1.5f * points[0].y();
		float baseHeight = 1.2f * points[0].y();
		float arrowHalfWidth = 0.5f * points[0].x();
		float baseHalfWidth = 0.3f * points[0].x();

		// pg3d().noStroke();
		// Arrow base
		pg().beginShape(PApplet.QUADS);
		if (isLeftHanded()) {
			vertex(-baseHalfWidth, -points[0].y(), -points[0].z());
			vertex(baseHalfWidth, -points[0].y(), -points[0].z());
			vertex(baseHalfWidth, -baseHeight, -points[0].z());
			vertex(-baseHalfWidth, -baseHeight, -points[0].z());
		} else {
			vertex(-baseHalfWidth, points[0].y(), -points[0].z());
			vertex(baseHalfWidth, points[0].y(), -points[0].z());
			vertex(baseHalfWidth, baseHeight, -points[0].z());
			vertex(-baseHalfWidth, baseHeight, -points[0].z());
		}
		pg().endShape();

		// Arrow
		pg().beginShape(PApplet.TRIANGLES);
		if (isLeftHanded()) {
			vertex(0.0f, -arrowHeight, -points[0].z());
			vertex(-arrowHalfWidth, -baseHeight, -points[0].z());
			vertex(arrowHalfWidth, -baseHeight, -points[0].z());
		} else {
			vertex(0.0f, arrowHeight, -points[0].z());
			vertex(-arrowHalfWidth, baseHeight, -points[0].z());
			vertex(arrowHalfWidth, baseHeight, -points[0].z());
		}
		pg().endShape();
		popModelView();
		pg().popStyle();
	}

	@Override
	public void drawPath(KeyFrameInterpolator kfi, int mask, int nbFrames, float scale) {
		pg().pushStyle();
		if (mask != 0) {
			int nbSteps = 30;
			pg().strokeWeight(2 * pg().strokeWeight);
			pg().noFill();

			List<Frame> path = kfi.path();
			if (((mask & 1) != 0) && path.size() > 1) {
				pg().beginShape();
				for (Frame myFr : path)
					vertex(myFr.position().x(), myFr.position().y(), myFr.position().z());
				pg().endShape();
			}
			if ((mask & 6) != 0) {
				int count = 0;
				if (nbFrames > nbSteps)
					nbFrames = nbSteps;
				float goal = 0.0f;

				for (Frame myFr : path)
					if ((count++) >= goal) {
						goal += nbSteps / (float) nbFrames;
						pushModelView();

						applyTransformation(myFr);

						if ((mask & 2) != 0)
							drawKFIEye(scale);
						if ((mask & 4) != 0)
							drawAxes(scale / 10.0f);

						popModelView();
					}
			}
			kfi.addFramesToAllAgentPools();
			pg().strokeWeight(pg().strokeWeight / 2f);
			drawPickingTargets(true);
		}
		pg().popStyle();
	}

	@Override
	protected void drawKFIEye(float scale) {
		pg().pushStyle();
		float halfHeight = scale * (is2D() ? 1.2f : 0.07f);
		float halfWidth = halfHeight * 1.3f;
		float dist = halfHeight / (float) Math.tan(PApplet.PI / 8.0f);

		float arrowHeight = 1.5f * halfHeight;
		float baseHeight = 1.2f * halfHeight;
		float arrowHalfWidth = 0.5f * halfWidth;
		float baseHalfWidth = 0.3f * halfWidth;

		// Frustum outline
		pg().noFill();
		pg().beginShape();
		vertex(-halfWidth, halfHeight, -dist);
		vertex(-halfWidth, -halfHeight, -dist);
		vertex(0.0f, 0.0f, 0.0f);
		vertex(halfWidth, -halfHeight, -dist);
		vertex(-halfWidth, -halfHeight, -dist);
		pg().endShape();
		pg().noFill();
		pg().beginShape();
		vertex(halfWidth, -halfHeight, -dist);
		vertex(halfWidth, halfHeight, -dist);
		vertex(0.0f, 0.0f, 0.0f);
		vertex(-halfWidth, halfHeight, -dist);
		vertex(halfWidth, halfHeight, -dist);
		pg().endShape();

		// Up arrow
		pg().noStroke();
		pg().fill(pg().strokeColor);
		// Base
		pg().beginShape(PApplet.QUADS);

		if (isLeftHanded()) {
			vertex(baseHalfWidth, -halfHeight, -dist);
			vertex(-baseHalfWidth, -halfHeight, -dist);
			vertex(-baseHalfWidth, -baseHeight, -dist);
			vertex(baseHalfWidth, -baseHeight, -dist);
		}
		else {
			vertex(-baseHalfWidth, halfHeight, -dist);
			vertex(baseHalfWidth, halfHeight, -dist);
			vertex(baseHalfWidth, baseHeight, -dist);
			vertex(-baseHalfWidth, baseHeight, -dist);
		}

		pg().endShape();
		// Arrow
		pg().beginShape(PApplet.TRIANGLES);

		if (isLeftHanded()) {
			vertex(0.0f, -arrowHeight, -dist);
			vertex(arrowHalfWidth, -baseHeight, -dist);
			vertex(-arrowHalfWidth, -baseHeight, -dist);
		}
		else {
			vertex(0.0f, arrowHeight, -dist);
			vertex(-arrowHalfWidth, baseHeight, -dist);
			vertex(arrowHalfWidth, baseHeight, -dist);
		}
		pg().endShape();
		pg().popStyle();
	}

	@Override
	public void drawCross(float px, float py, float size) {
		float half_size = size / 2f;
		pg().pushStyle();
		beginScreenDrawing();
		pg().noFill();
		pg().beginShape(LINES);
		vertex(px - half_size, py);
		vertex(px + half_size, py);
		vertex(px, py - half_size);
		vertex(px, py + half_size);
		pg().endShape();
		endScreenDrawing();
		pg().popStyle();
	}

	@Override
	public void drawFilledCircle(int subdivisions, Vec center, float radius) {
		pg().pushStyle();
		float precision = PApplet.TWO_PI / subdivisions;
		float x = center.x();
		float y = center.y();
		float angle, x2, y2;
		beginScreenDrawing();
		pg().noStroke();
		pg().beginShape(TRIANGLE_FAN);
		vertex(x, y);
		for (angle = 0.0f; angle <= PApplet.TWO_PI + 1.1 * precision; angle += precision) {
			x2 = x + PApplet.sin(angle) * radius;
			y2 = y + PApplet.cos(angle) * radius;
			vertex(x2, y2);
		}
		pg().endShape();
		endScreenDrawing();
		pg().popStyle();
	}

	@Override
	public void drawFilledSquare(Vec center, float edge) {
		float half_edge = edge / 2f;
		pg().pushStyle();
		float x = center.x();
		float y = center.y();
		beginScreenDrawing();
		pg().noStroke();
		pg().beginShape(QUADS);
		vertex(x - half_edge, y + half_edge);
		vertex(x + half_edge, y + half_edge);
		vertex(x + half_edge, y - half_edge);
		vertex(x - half_edge, y - half_edge);
		pg().endShape();
		endScreenDrawing();
		pg().popStyle();
	}

	@Override
	public void drawShooterTarget(Vec center, float length) {
		float half_length = length / 2f;
		pg().pushStyle();
		float x = center.x();
		float y = center.y();
		beginScreenDrawing();
		pg().noFill();

		pg().beginShape();
		vertex((x - half_length), (y - half_length) + (0.6f * half_length));
		vertex((x - half_length), (y - half_length));
		vertex((x - half_length) + (0.6f * half_length), (y - half_length));
		pg().endShape();

		pg().beginShape();
		vertex((x + half_length) - (0.6f * half_length), (y - half_length));
		vertex((x + half_length), (y - half_length));
		vertex((x + half_length), ((y - half_length) + (0.6f * half_length)));
		pg().endShape();

		pg().beginShape();
		vertex((x + half_length), ((y + half_length) - (0.6f * half_length)));
		vertex((x + half_length), (y + half_length));
		vertex(((x + half_length) - (0.6f * half_length)), (y + half_length));
		pg().endShape();

		pg().beginShape();
		vertex((x - half_length) + (0.6f * half_length), (y + half_length));
		vertex((x - half_length), (y + half_length));
		vertex((x - half_length), ((y + half_length) - (0.6f * half_length)));
		pg().endShape();
		endScreenDrawing();
		drawCross(center.x(), center.y(), 0.6f * length);
		pg().popStyle();
	}

	@Override
	public void drawPickingTargets(boolean keyFrame) {
		pg().pushStyle();
		for (Grabber mg : inputHandler().globalGrabberList()) {
			if (mg instanceof InteractiveFrame) {
				InteractiveFrame iF = (InteractiveFrame) mg;// downcast needed
				// frames
				if (!(iF.isInEyePath() ^ keyFrame)) {
					Vec center = projectedCoordinatesOf(iF.position());
					if (grabsAnyAgentInput(mg)) {
						pg().pushStyle();
						pg().strokeWeight(2 * pg().strokeWeight);
						pg().colorMode(HSB, 255);
						float hue = pg().hue(pg().strokeColor);
						float saturation = pg().saturation(pg().strokeColor);
						float brightness = pg().brightness(pg().strokeColor);
						pg().stroke(hue, saturation * 1.4f, brightness * 1.4f);
						drawShooterTarget(center, (iF.grabsInputThreshold() + 1));
						pg().popStyle();
					}
					else {
						pg().pushStyle();
						pg().colorMode(HSB, 255);
						float hue = pg().hue(pg().strokeColor);
						float saturation = pg().saturation(pg().strokeColor);
						float brightness = pg().brightness(pg().strokeColor);
						pg().stroke(hue, saturation * 1.4f, brightness);
						drawShooterTarget(center, iF.grabsInputThreshold());
						pg().popStyle();
					}
				}
			}
		}
		pg().popStyle();
	}

	/**
	 * Code contributed by Jacques Maire (http://www.alcys.com/) See also:
	 * http://www.mathcurve.com/courbes3d/solenoidtoric/solenoidtoric.shtml
	 * http://crazybiocomputing.blogspot.fr/2011/12/3d-curves-toric-solenoids.html
	 */
	@Override
	public void drawTorusSolenoid(int faces, int detail, float insideRadius, float outsideRadius) {
		pg().pushStyle();
		pg().noStroke();
		Vec v1, v2;
		int b, ii, jj, a;
		float eps = PApplet.TWO_PI / detail;
		for (a = 0; a < faces; a += 2) {
			pg().beginShape(PApplet.TRIANGLE_STRIP);
			b = (a <= (faces - 1)) ? a + 1 : 0;
			for (int i = 0; i < (detail + 1); i++) {
				ii = (i < detail) ? i : 0;
				jj = ii + 1;
				float ai = eps * jj;
				float alpha = a * PApplet.TWO_PI / faces + ai;
				v1 = new Vec((outsideRadius + insideRadius * PApplet.cos(alpha)) * PApplet.cos(ai),
						(outsideRadius + insideRadius * PApplet.cos(alpha)) * PApplet.sin(ai), insideRadius
								* PApplet.sin(alpha));
				alpha = b * PApplet.TWO_PI / faces + ai;
				v2 = new Vec((outsideRadius + insideRadius * PApplet.cos(alpha)) * PApplet.cos(ai),
						(outsideRadius + insideRadius * PApplet.cos(alpha)) * PApplet.sin(ai), insideRadius
								* PApplet.sin(alpha));
				vertex(v1.x(), v1.y(), v1.z());
				vertex(v2.x(), v2.y(), v2.z());
			}
			pg().endShape();
		}
		pg().popStyle();
	}

	/*
	 * Copy paste from AbstractScene but we add the style (color, stroke, etc) here.
	 */
	@Override
	protected void drawAxesHint() {
		pg().pushStyle();
		pg().strokeWeight(2);
		drawAxes(eye().sceneRadius());
		pg().popStyle();
	}

	/*
	 * Copy paste from AbstractScene but we add the style (color, stroke, etc) here.
	 */
	@Override
	protected void drawGridHint() {
		pg().pushStyle();
		pg().stroke(170);
		if (gridIsDotted()) {
			pg().strokeWeight(2);
			drawDottedGrid(eye().sceneRadius());
		}
		else {
			pg().strokeWeight(1);
			drawGrid(eye().sceneRadius());
		}
		pg().popStyle();
	}

	/*
	 * Copy paste from AbstractScene but we add the style (color, stroke, etc) here.
	 */
	@Override
	protected void drawPathsHint() {
		pg().pushStyle();
		pg().colorMode(PApplet.RGB, 255);
		pg().strokeWeight(1);
		pg().stroke(0, 220, 220);
		drawEyePaths();
		pg().popStyle();
	}

	@Override
	/*
	 * Copy paste from AbstractScene but we add the style (color, stroke, etc) here.
	 */
	protected void drawPickingHint() {
		pg().pushStyle();
		pg().colorMode(PApplet.RGB, 255);
		pg().strokeWeight(1);
		pg().stroke(220, 220, 220);
		drawPickingTargets();
		pg().popStyle();
	}

	@Override
	protected void drawAnchorHint() {
		pg().pushStyle();
		Vec p = eye().projectedCoordinatesOf(anchor());
		pg().stroke(255);
		pg().strokeWeight(3);
		drawCross(p.vec[0], p.vec[1]);
		pg().popStyle();
	}

	@Override
	protected void drawPointUnderPixelHint() {
		pg().pushStyle();
		Vec v = eye().projectedCoordinatesOf(eye().frame().pupVec);
		pg().stroke(255);
		pg().strokeWeight(3);
		drawCross(v.vec[0], v.vec[1], 30);
		pg().popStyle();
	}

	@Override
	protected void drawScreenRotateHint() {
		pg().pushStyle();
		if (!(motionAgent() instanceof MouseAgent))
			return;
		float p1x = mouseAgent().lastEvent().x() - originCorner().x();
		float p1y = mouseAgent().lastEvent().y() - originCorner().y();
		Vec p2 = eye().projectedCoordinatesOf(anchor());
		beginScreenDrawing();
		pg().stroke(255, 255, 255);
		pg().strokeWeight(2);
		pg().noFill();
		line(p2.x(), p2.y(), p1x, p1y);
		endScreenDrawing();
		pg().popStyle();
	}

	@Override
	protected void drawZoomWindowHint() {
		if (!(motionAgent() instanceof MouseAgent))
			return;
		pg().pushStyle();
		float p1x = mouseAgent().pressEvent().x() - originCorner().x();
		float p1y = mouseAgent().pressEvent().y() - originCorner().y();
		float p2x = mouseAgent().lastEvent().x() - originCorner().x();
		float p2y = mouseAgent().lastEvent().y() - originCorner().y();
		beginScreenDrawing();
		pg().stroke(255, 255, 255);
		pg().strokeWeight(2);
		pg().noFill();
		pg().beginShape();
		vertex(p1x, p1y);
		vertex(p2x, p1y);
		vertex(p2x, p2y);
		vertex(p1x, p2y);
		pg().endShape(CLOSE);
		endScreenDrawing();
		pg().popStyle();
	}

	// decide whether or not to include these in the 2.1 release:
	/*
	 * 
	 * // PVector <-> toVec
	 * 
	 * public void drawArrow(PVector from, PVector to, float radius) { drawArrow(Scene.toVec(from), Scene.toVec(to),
	 * radius); }
	 * 
	 * public void drawFilledCircle(PVector center, float radius) { drawFilledCircle(Scene.toVec(center), radius); }
	 * 
	 * public void drawHollowCylinder(int detail, float w, float h, PVector m, PVector n) { drawHollowCylinder(detail, w,
	 * h, Scene.toVec(m), Scene.toVec(n)); }
	 * 
	 * public void drawFilledSquare(PVector center, float edge) { drawFilledSquare(Scene.toVec(center), edge); }
	 * 
	 * public void drawShooterTarget(PVector center, float length) { drawShooterTarget(Scene.toVec(center), length); }
	 * 
	 * public boolean isPointVisible(PVector point) { return isPointVisible(Scene.toVec(point)); }
	 * 
	 * public Eye.Visibility ballVisibility(PVector center, float radius) { return ballVisibility(Scene.toVec(center),
	 * radius); }
	 * 
	 * public Eye.Visibility boxVisibility(PVector p1, PVector p2) { return boxVisibility(Scene.toVec(p1),
	 * Scene.toVec(p2)); }
	 * 
	 * public boolean isFaceBackFacing(PVector a, PVector b, PVector c) { return isFaceBackFacing(Scene.toVec(a),
	 * Scene.toVec(b), Scene.toVec(c)); }
	 * 
	 * public PVector worldPointUnderPixel(Point pixel) { return Scene.toPVector(pointUnderPixel(pixel)); }
	 * 
	 * public PVector eyeProjectedCoordinatesOf(PVector src) { return
	 * Scene.toPVector(projectedCoordinatesOf(Scene.toVec(src))); }
	 * 
	 * public PVector eyeUnprojectedCoordinatesOf(PVector src) { return
	 * Scene.toPVector(unprojectedCoordinatesOf(Scene.toVec(src))); }
	 * 
	 * public PVector sceneCenter() { return Scene.toPVector(center()); }
	 * 
	 * public void setCenter(PVector center) { setCenter(Scene.toVec(center)); }
	 * 
	 * public PVector sceneAnchor() { return Scene.toPVector(anchor()); }
	 * 
	 * public void setAnchor(PVector anchor) { setAnchor(Scene.toVec(anchor)); }
	 * 
	 * public void setBoundingBox(PVector min, PVector max) { setBoundingBox(Scene.toVec(min),Scene.toVec(max)); }
	 * 
	 * public void setBoundingRect(PVector min, PVector max) { setBoundingRect(Scene.toVec(min), Scene.toVec(max)); }
	 * 
	 * //PMatrix <-> toMat
	 * 
	 * public void applyModelView(PMatrix2D source) { applyModelView(Scene.toMat(source)); }
	 * 
	 * public void applyModelView(PMatrix3D source) { applyModelView(Scene.toMat(source)); }
	 * 
	 * public void applyProjection(PMatrix3D source) { applyProjection(Scene.toMat(source)); }
	 * 
	 * public PMatrix2D modelViewMatrix2D() { return Scene.toPMatrix2D(modelView()); }
	 * 
	 * public PMatrix3D modelViewMatrix() { return Scene.toPMatrix(modelView()); }
	 * 
	 * public PMatrix3D projectionMatrix() { return Scene.toPMatrix(projection()); }
	 * 
	 * public void setModelView(PMatrix2D source) { setModelView(Scene.toMat(source)); }
	 * 
	 * public void setModelView(PMatrix3D source) { setModelView(Scene.toMat(source)); }
	 * 
	 * public void setProjection(PMatrix3D source) { setProjection(Scene.toMat(source)); } //
	 */
}