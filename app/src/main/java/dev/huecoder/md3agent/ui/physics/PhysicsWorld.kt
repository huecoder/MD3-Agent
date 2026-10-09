package dev.huecoder.md3agent.ui.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.dyn4j.dynamics.Body
import org.dyn4j.dynamics.contact.Contact
import org.dyn4j.dynamics.joint.PinJoint
import org.dyn4j.geometry.Convex
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType
import org.dyn4j.geometry.Vector2
import org.dyn4j.geometry.hull.GiftWrap
import org.dyn4j.world.ContactCollisionData
import org.dyn4j.world.World
import org.dyn4j.world.listener.ContactListenerAdapter
import kotlin.math.abs

data class Material(val restitution: Double, val friction: Double, val density: Double = 1.0) {
    companion object {
        val Soft = Material(restitution = 0.35, friction = 0.6)
        val Bouncy = Material(restitution = 0.5, friction = 0.4)
        val Grippy = Material(restitution = 0.25, friction = 0.9)
    }
}

data class Impact(val first: Body, val second: Body, val point: Offset, val speed: Float)

class PhysicsWorld(private val pixelsPerMeter: Float, gravity: Double = CARTOON_GRAVITY) {
    val gravityPixels: Float = (gravity * pixelsPerMeter).toFloat()

    fun pointGravity(direction: Offset) {
        val length = direction.getDistance().takeIf { it > 0f } ?: return
        val strength = gravityPixels / pixelsPerMeter / length
        world.setGravity((direction.x * strength).toDouble(), (direction.y * strength).toDouble())
        world.bodies.forEach { it.setAtRest(false) }
    }

    fun bodyAt(point: Offset): Body? {
        val probe = Vector2(point.x.meters, point.y.meters)
        return world.bodies.lastOrNull { !it.mass.isInfinite && it.contains(probe) }
    }

    private val world = World<Body>().apply {
        setGravity(0.0, gravity)
        settings.maximumTranslation = MAX_TRANSLATION_METERS
    }
    private var onImpact: (Impact) -> Unit = {}

    init {
        world.addContactListener(object : ContactListenerAdapter<Body>() {
            override fun begin(collision: ContactCollisionData<Body>, contact: Contact) {
                val constraint = collision.contactConstraint
                val first = constraint.body1
                val second = constraint.body2
                val closing = first.linearVelocity.difference(second.linearVelocity).dot(constraint.normal)
                onImpact(Impact(first, second, contact.point.toPixels(), abs(closing).toFloat() * pixelsPerMeter))
            }
        })
    }

    fun onImpact(listener: (Impact) -> Unit) {
        onImpact = listener
    }

    fun addWall(rect: Rect, material: Material = Material.Soft): Body = Body().apply {
        addFixture(Geometry.createRectangle(rect.width.meters, rect.height.meters), 1.0, material.friction, material.restitution)
        setMass(MassType.INFINITE)
        translate(rect.center.x.meters, rect.center.y.meters)
        world.addBody(this)
    }

    fun addBall(center: Offset, radius: Float, material: Material): Body =
        addBody(center, Geometry.createCircle(radius.meters), material)

    fun addHull(center: Offset, outline: List<Offset>, material: Material): Body {
        val hull = GiftWrap().generate(*outline.map { Vector2(it.x.meters, it.y.meters) }.toTypedArray())
        return addBody(center, Geometry.createPolygon(*hull), material)
    }

    fun remove(body: Body) {
        world.removeBody(body)
    }

    fun step(seconds: Float) {
        world.update(seconds.coerceAtMost(MAX_FRAME_SECONDS).toDouble())
    }

    fun position(body: Body): Offset = body.worldCenter.toPixels()

    fun origin(body: Body): Offset = Offset(body.transform.translationX.toFloat(), body.transform.translationY.toFloat()) * pixelsPerMeter

    fun angle(body: Body): Float = body.transform.rotationAngle.toFloat()

    fun velocity(body: Body): Offset = body.linearVelocity.toPixels()

    fun setVelocity(body: Body, pixelsPerSecond: Offset) {
        body.setLinearVelocity(pixelsPerSecond.x.meters, pixelsPerSecond.y.meters)
        body.setAtRest(false)
    }

    fun moveTo(body: Body, center: Offset, angle: Double = 0.0) {
        body.transform.identity()
        body.rotate(angle)
        body.translate(center.x.meters, center.y.meters)
        body.setLinearVelocity(0.0, 0.0)
        body.angularVelocity = 0.0
        body.setAtRest(false)
    }

    fun driveSpin(body: Body, radiansPerSecond: Double, maxAcceleration: Double) {
        val acceleration = ((radiansPerSecond - body.angularVelocity) * SPIN_RESPONSE).coerceIn(-maxAcceleration, maxAcceleration)
        body.applyTorque(acceleration * body.mass.inertia)
    }

    fun grab(body: Body, at: Offset): Grab = Grab(body, at)

    inner class Grab internal constructor(val body: Body, at: Offset) {
        private val joint = PinJoint(body, Vector2(at.x.meters, at.y.meters)).apply {
            isSpringEnabled = true
            isSpringDamperEnabled = true
            springFrequency = GRAB_FREQUENCY
            springDampingRatio = GRAB_DAMPING
            maximumSpringForce = GRAB_MAX_FORCE * body.mass.mass
            isMaximumSpringForceEnabled = true
        }

        init {
            world.addJoint(joint)
            body.setAtRest(false)
        }

        fun moveTo(target: Offset) {
            joint.setTarget(target.x.meters, target.y.meters)
            body.setAtRest(false)
        }

        fun release() {
            world.removeJoint(joint)
        }
    }

    private fun addBody(center: Offset, shape: Convex, material: Material): Body = Body().apply {
        addFixture(shape, material.density, material.friction, material.restitution)
        setMass(MassType.NORMAL)
        translate(center.x.meters, center.y.meters)
        world.addBody(this)
    }

    private val Float.meters: Double get() = (this / pixelsPerMeter).toDouble()

    private fun Vector2.toPixels() = Offset((x * pixelsPerMeter).toFloat(), (y * pixelsPerMeter).toFloat())

    private companion object {
        const val CARTOON_GRAVITY = 24.0
        const val MAX_FRAME_SECONDS = 1f / 30f
        const val MAX_TRANSLATION_METERS = 4.0
        const val SPIN_RESPONSE = 8.0
        const val GRAB_FREQUENCY = 6.0
        const val GRAB_DAMPING = 0.7
        const val GRAB_MAX_FORCE = 1_500.0
    }
}
