package com.pushpendra.pocsphere

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

data class DetectionBox(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val color: Int
)

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var detections: List<DetectionBox> = emptyList()
    var boxScaleX: Float = 1f
    var boxScaleY: Float = 1f

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        style = Paint.Style.FILL
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
    }
    
    private val textBackgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        setShadowLayer(2f, 0f, 0f, Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        for (detection in detections) {
            // Draw bounding box
            boxPaint.color = detection.color
            val left = detection.boundingBox.left * boxScaleX
            val top = detection.boundingBox.top * boxScaleY
            val right = detection.boundingBox.right * boxScaleX
            val bottom = detection.boundingBox.bottom * boxScaleY
            
            canvas.drawRect(left, top, right, bottom, boxPaint)
            
            // Draw label with background
            val label = "${detection.label} ${(detection.confidence * 100).toInt()}%"
            val textBounds = Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            
            val textX = left
            val textY = top - 10
            
            // Draw text background
            textBackgroundPaint.color = detection.color
            canvas.drawRect(
                textX - 5,
                textY - textBounds.height() - 5,
                textX + textBounds.width() + 5,
                textY + 5,
                textBackgroundPaint
            )
            
            // Draw text
            canvas.drawText(label, textX, textY, textPaint)
        }
    }
} 