/*********************************************************************************
 * dandelion_tree
 * Copyright (c) 2014 National University of Colombia, https://github.com/remixlab
 * @author Jean Pierre Charalambos, http://otrolado.info/
 *
 * All rights reserved. Library that eases the creation of interactive
 * scenes, released under the terms of the GNU Public License v3.0
 * which is available at http://www.gnu.org/licenses/gpl.html
 *********************************************************************************/

package remixlab.dandelion.constraint;

import remixlab.dandelion.geom.*;

/**
 * An interface class for Frame constraints.
 * <p>
 * This class defines the interface for the constraint that can be applied to a Frame to limit its motion. Use
 * {@link remixlab.dandelion.geom.RefFrame#setConstraint(Constraint)} to associate a Constraint to a Frame (default is a
 * {@code null} {@link remixlab.dandelion.geom.RefFrame#constraint()}.
 */
public abstract class Constraint {
	/**
	 * Filters the translation applied to the Frame. This default implementation is empty (no filtering).
	 * <p>
	 * Overload this method in your own Constraint class to define a new translation constraint. {@code frame} is the
	 * Frame to which is applied the translation. You should refrain from directly changing its value in the constraint.
	 * Use its {@link remixlab.dandelion.geom.RefFrame#position()} and update the translation accordingly instead.
	 * <p>
	 * {@code translation} is expressed in the local Frame coordinate system. Use
	 * {@link remixlab.dandelion.geom.RefFrame#inverseTransformOf(Vec)} to express it in the world coordinate system if
	 * needed.
	 */
	public Vec constrainTranslation(Vec translation, RefFrame frame) {
		return translation.get();
	}

	/**
	 * Filters the rotation applied to the {@code frame}. This default implementation is empty (no filtering).
	 * <p>
	 * Overload this method in your own Constraint class to define a new rotation constraint. See
	 * {@link #constrainTranslation(Vec, RefFrame)} for details.
	 * <p>
	 * Use {@link remixlab.dandelion.geom.RefFrame#inverseTransformOf(Vec)} on the {@code rotation}
	 * {@link remixlab.dandelion.geom.Quat#axis()} to express {@code rotation} in the world coordinate system if needed.
	 */
	public Rotation constrainRotation(Rotation rotation, RefFrame frame) {
		return rotation.get();
	}
}
