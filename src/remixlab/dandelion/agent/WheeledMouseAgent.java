/*********************************************************************************
 * dandelion_tree
 * Copyright (c) 2014 National University of Colombia, https://github.com/remixlab
 * @author Jean Pierre Charalambos, http://otrolado.info/
 *
 * All rights reserved. Library that eases the creation of interactive
 * scenes, released under the terms of the GNU Public License v3.0
 * which is available at http://www.gnu.org/licenses/gpl.html
 *********************************************************************************/

package remixlab.dandelion.agent;

import remixlab.bias.core.Action;
import remixlab.bias.core.BogusEvent;
import remixlab.bias.core.EventGrabberTuple;
import remixlab.bias.event.DOF2Event;
import remixlab.bias.event.MotionEvent;
import remixlab.dandelion.core.*;

/**
 * An {@link remixlab.dandelion.agent.ActionWheeledBiMotionAgent} representing a Wheeled mouse and thus only holds 2
 * Degrees-Of-Freedom (e.g., two translations or two rotations), such as most mice.
 */
public class WheeledMouseAgent extends WheeledPointingAgent {
	boolean						bypassNullEvent, need4Spin, drive, rotateMode;
	// DOF2Event pressEvent;
	float							dFriction;
	InteractiveFrame	iFrame;

	/**
	 * Constructs a MouseAgent. Nothing fancy.
	 * 
	 * @param scn
	 *          AbstractScene
	 * @param n
	 *          Agents name
	 */
	public WheeledMouseAgent(AbstractScene scn, String n) {
		super(scn, n);
	}

	/**
	 * Call {@link #updateTrackedGrabber(BogusEvent)} on the given event.
	 */
	public void move(DOF2Event e) {
		lastEvent = e;
		updateTrackedGrabber(lastEvent);
	}

	/**
	 * Begin interaction and call {@link #handle(BogusEvent)} on the given event. Keeps track of the {@link #pressEvent()}
	 * .
	 */
	public void press(DOF2Event e) {
		lastEvent = e;
		pressEvent = lastEvent.get();
		if (inputGrabber() instanceof InteractiveFrame) {
			if (need4Spin)
				((InteractiveFrame) inputGrabber()).stopSpinning();
			iFrame = (InteractiveFrame) inputGrabber();
			Action<?> a = (inputGrabber() instanceof InteractiveEyeFrame) ? eyeProfile().handle((BogusEvent) lastEvent)
					: frameProfile().handle((BogusEvent) lastEvent);
			if (a == null)
				return;
			DandelionAction dA = (DandelionAction) a.referenceAction();
			if (dA == DandelionAction.SCREEN_TRANSLATE)
				((InteractiveFrame) inputGrabber()).dirIsFixed = false;
			rotateMode = ((dA == DandelionAction.ROTATE) || (dA == DandelionAction.ROTATE_XYZ)
					|| (dA == DandelionAction.ROTATE_CAD)
					|| (dA == DandelionAction.SCREEN_ROTATE) || (dA == DandelionAction.TRANSLATE_XYZ_ROTATE_XYZ));
			if (rotateMode && scene.is3D())
				scene.camera().frame().cadRotationIsReversed = scene.camera().frame()
						.transformOf(scene.camera().frame().sceneUpVector()).y() < 0.0f;
			need4Spin = (rotateMode && (((InteractiveFrame) inputGrabber()).dampingFriction() == 0));
			drive = (dA == DandelionAction.DRIVE);
			bypassNullEvent = (dA == DandelionAction.MOVE_FORWARD) || (dA == DandelionAction.MOVE_BACKWARD)
					|| (drive) && scene.inputHandler().isAgentRegistered(this);
			scene.setZoomVisualHint(dA == DandelionAction.ZOOM_ON_REGION && (inputGrabber() instanceof InteractiveEyeFrame)
					&& scene.inputHandler().isAgentRegistered(this));
			scene.setRotateVisualHint(dA == DandelionAction.SCREEN_ROTATE && (inputGrabber() instanceof InteractiveFrame)
					&& scene.inputHandler().isAgentRegistered(this));
			if (bypassNullEvent || scene.zoomVisualHint() || scene.rotateVisualHint()) {
				if (bypassNullEvent) {
					// This is needed for first person:
					((InteractiveFrame) inputGrabber()).updateSceneUpVector();
					dFriction = ((InteractiveFrame) inputGrabber()).dampingFriction();
					((InteractiveFrame) inputGrabber()).setDampingFriction(0);
					handler.eventTupleQueue().add(new EventGrabberTuple(lastEvent, a, inputGrabber()));
				}
			}
			else
				handle(lastEvent);
		} else
			handle(lastEvent);
	}

	/**
	 * Call {@link #handle(BogusEvent)} on the given event.
	 */
	public void drag(DOF2Event e) {
		lastEvent = e;
		if (!scene.zoomVisualHint()) { // bypass zoom_on_region, may be different when using a touch device :P
			if (drive && inputGrabber() instanceof InteractiveFrame)
				((InteractiveFrame) inputGrabber()).setFlySpeed(0.01f * scene.radius() * 0.01f
						* (lastEvent.y() - pressEvent.y()));
			// never handle ZOOM_ON_REGION on a drag. Could happen if user presses a modifier during drag triggering it
			Action<?> a = (inputGrabber() instanceof InteractiveEyeFrame) ? eyeProfile().handle((BogusEvent) lastEvent)
					: frameProfile().handle((BogusEvent) lastEvent);
			if (a == null)
				return;
			DandelionAction dA = (DandelionAction) a.referenceAction();
			if (dA != DandelionAction.ZOOM_ON_REGION)
				handle(lastEvent);
		}
	}

	/**
	 * Ends interaction and calls {@link #updateTrackedGrabber(BogusEvent)} on the given event.
	 */
	public void release(DOF2Event e) {
		DOF2Event prevEvent = lastEvent().get();
		lastEvent = e;
		if (inputGrabber() instanceof InteractiveFrame)
			// note that the following two lines fail on event when need4Spin
			if (need4Spin && (prevEvent.speed() >= ((InteractiveFrame) inputGrabber()).spinningSensitivity()))
				((InteractiveFrame) inputGrabber()).startSpinning(prevEvent);
		if (scene.zoomVisualHint()) {
			// at first glance this should work
			// handle(event);
			// but the problem is that depending on the order the button and the modifiers are released,
			// different actions maybe triggered, so we go for sure ;) :
			lastEvent.setPreviousEvent(pressEvent);
			enqueueEventTuple(new EventGrabberTuple(lastEvent, DOF2Action.ZOOM_ON_REGION, inputGrabber()));
			scene.setZoomVisualHint(false);
		}
		if (scene.rotateVisualHint())
			scene.setRotateVisualHint(false);
		updateTrackedGrabber(lastEvent);
		if (bypassNullEvent) {
			iFrame.setDampingFriction(dFriction);
			bypassNullEvent = !bypassNullEvent;
		}
		// restore speed after drive action terminates:
		if (drive && inputGrabber() instanceof InteractiveFrame)
			((InteractiveFrame) inputGrabber()).setFlySpeed(0.01f * scene.radius());
	}

	// HIGH-LEVEL

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
	 * @see #setAsFirstPerson()
	 * @see #setAsThirdPerson()
	 */
	@Override
	public void setAsArcball() {
		resetAllProfiles();
		eyeProfile().setBinding(buttonModifiersFix(left), left, DOF2Action.ROTATE);
		eyeProfile().setBinding(buttonModifiersFix(center), center, scene.is3D() ? DOF2Action.ZOOM : DOF2Action.SCALE);
		eyeProfile().setBinding(buttonModifiersFix(right), right, DOF2Action.TRANSLATE);
		eyeProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, left), left, DOF2Action.ZOOM_ON_REGION);
		eyeProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, center), center, DOF2Action.SCREEN_TRANSLATE);
		eyeProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, right), right, DOF2Action.SCREEN_ROTATE);
		frameProfile().setBinding(buttonModifiersFix(left), left, DOF2Action.ROTATE);
		frameProfile().setBinding(buttonModifiersFix(center), center, DOF2Action.SCALE);
		frameProfile().setBinding(buttonModifiersFix(right), right, DOF2Action.TRANSLATE);
		frameProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, center), center, DOF2Action.SCREEN_TRANSLATE);
		frameProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, right), right, DOF2Action.SCREEN_ROTATE);
		setCommonBindings();
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
	 * @see #setAsArcball()
	 * @see #setAsThirdPerson()
	 */
	@Override
	public void setAsFirstPerson() {
		resetAllProfiles();
		eyeProfile().setBinding(buttonModifiersFix(left), left, DOF2Action.MOVE_FORWARD);
		eyeProfile().setBinding(buttonModifiersFix(right), right, DOF2Action.MOVE_BACKWARD);
		eyeProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, left), left, DOF2Action.ROTATE_Z);
		eyeWheelProfile().setBinding(MotionEvent.CTRL, MotionEvent.NOBUTTON, DOF1Action.ROTATE_Z);
		if (scene.is3D()) {
			eyeProfile().setBinding(buttonModifiersFix(center), center, DOF2Action.LOOK_AROUND);
			eyeProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, center), center, DOF2Action.DRIVE);
		}
		frameProfile().setBinding(buttonModifiersFix(left), left, DOF2Action.ROTATE);
		frameProfile().setBinding(buttonModifiersFix(center), center, DOF2Action.SCALE);
		frameProfile().setBinding(buttonModifiersFix(right), right, DOF2Action.TRANSLATE);
		frameProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, center), center, DOF2Action.SCREEN_TRANSLATE);
		frameProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, right), right, DOF2Action.SCREEN_ROTATE);
		setCommonBindings();
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
	 * @see #setAsArcball()
	 * @see #setAsFirstPerson()
	 */
	@Override
	public void setAsThirdPerson() {
		resetAllProfiles();
		frameProfile().setBinding(buttonModifiersFix(left), left, DOF2Action.MOVE_FORWARD);
		frameProfile().setBinding(buttonModifiersFix(right), right, DOF2Action.MOVE_BACKWARD);
		frameProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, left), left, DOF2Action.ROTATE_Z);
		if (scene.is3D()) {
			frameProfile().setBinding(buttonModifiersFix(center), center, DOF2Action.LOOK_AROUND);
			frameProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, center), center, DOF2Action.DRIVE);
		}
		setCommonBindings();
	}

	/**
	 * Set the following (common) bindings:
	 * <p>
	 * 2 left clicks -> ALIGN_FRAME<br>
	 * 2right clicks -> CENTER_FRAME<br>
	 * Wheel in 2D -> SCALE both, InteractiveFrame and InteractiveEyeFrame<br>
	 * Wheel in 3D -> SCALE InteractiveFrame, and ZOOM InteractiveEyeFrame<br>
	 * <p>
	 * which are used in {@link #setAsArcball()}, {@link #setAsFirstPerson()} and {@link #setAsThirdPerson()}
	 */
	protected void setCommonBindings() {
		eyeClickProfile().setBinding(buttonModifiersFix(left), left, 2, ClickAction.ALIGN_FRAME);
		eyeClickProfile().setBinding(buttonModifiersFix(right), right, 2, ClickAction.CENTER_FRAME);
		frameClickProfile().setBinding(buttonModifiersFix(left), left, 2, ClickAction.ALIGN_FRAME);
		frameClickProfile().setBinding(buttonModifiersFix(right), right, 2, ClickAction.CENTER_FRAME);
		eyeWheelProfile().setBinding(MotionEvent.NOMODIFIER_MASK, MotionEvent.NOBUTTON,
				scene.is3D() ? DOF1Action.ZOOM : DOF1Action.SCALE);
		frameWheelProfile().setBinding(MotionEvent.NOMODIFIER_MASK, MotionEvent.NOBUTTON, DOF1Action.SCALE);
	}
}
