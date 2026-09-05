/*
   Copyright 2012 Harri Smatt

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package fi.harism.curl;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;

/**
 * Storage class for page textures, blend colors and possibly some other values
 * in the future.
 * 
 * @author harism
 */
public class CurlPage {

	public static final int SIDE_BACK = 2;
	public static final int SIDE_BOTH = 3;
	public static final int SIDE_FRONT = 1;

	private int mColorBack;
	private int mColorFront;
	private Bitmap mTextureBack;
	private Bitmap mTextureFront;
	private boolean mTexturesChanged;

	/**
	 * Default constructor.
	 */
	public CurlPage() {
		reset();
	}

// Project adaptation (thread safety): updatePages()/updatePage() run on the UI
// thread (setCurrentIndex/startCurl) while CurlMesh.onDrawFrame consumes the very
// same CurlPage on the GL render thread. Vanilla CurlPage mutated/recycled its
// bitmaps without synchronization - concurrent Bitmap.recycle() from two threads
// is a native double-free and crashed with SIGSEGV inside Bitmap_recycle when the
// reader switched page animation modes (repro: rapid CURL <-> SLIDE switching).
// All texture/mutation accessors are now synchronized on this CurlPage's monitor
// so producers (UI/GL-callback threads) and the consumer (GL draw) serialize.
/**
 * Getter for color.
 */
	public synchronized int getColor(int side) {
		switch (side) {
		case SIDE_FRONT:
			return mColorFront;
		default:
			return mColorBack;
		}
	}

	/**
	 * Calculates the next highest power of two for a given integer.
	 */
	private int getNextHighestPO2(int n) {
		n -= 1;
		n = n | (n >> 1);
		n = n | (n >> 2);
		n = n | (n >> 4);
		n = n | (n >> 8);
		n = n | (n >> 16);
		n = n | (n >> 32);
		return n + 1;
	}

	/**
	 * Generates nearest power of two sized Bitmap for give Bitmap. Returns this
	 * new Bitmap using default return statement + original texture coordinates
	 * are stored into RectF.
	 */
	private Bitmap getTexture(Bitmap bitmap, RectF textureRect) {
		// Bitmap original size.
		int w = bitmap.getWidth();
		int h = bitmap.getHeight();
		// Bitmap size expanded to next power of two. This is done due to
		// the requirement on many devices, texture width and height should
		// be power of two.
		int newW = getNextHighestPO2(w);
		int newH = getNextHighestPO2(h);

		// TODO: Is there another way to create a bigger Bitmap and copy
		// original Bitmap to it more efficiently? Immutable bitmap anyone?
		Bitmap bitmapTex = Bitmap.createBitmap(newW, newH, bitmap.getConfig());
		Canvas c = new Canvas(bitmapTex);
		c.drawBitmap(bitmap, 0, 0, null);

		// Calculate final texture coordinates.
		float texX = (float) w / newW;
		float texY = (float) h / newH;
		textureRect.set(0f, 0f, texX, texY);

		return bitmapTex;
	}

	/**
	 * Getter for textures. Creates Bitmap sized to nearest power of two, copies
	 * original Bitmap into it and returns it. RectF given as parameter is
	 * filled with actual texture coordinates in this new upscaled texture
	 * Bitmap.
	 */
	public synchronized Bitmap getTexture(RectF textureRect, int side) {
		switch (side) {
		case SIDE_FRONT:
			return getTexture(mTextureFront, textureRect);
		default:
			return getTexture(mTextureBack, textureRect);
		}
	}

	/**
	 * Returns true if textures have changed.
	 */
	public synchronized boolean getTexturesChanged() {
		return mTexturesChanged;
	}

	/**
	 * Returns true if back siding texture exists and it differs from front
	 * facing one.
	 */
	public synchronized boolean hasBackTexture() {
		return !mTextureFront.equals(mTextureBack);
	}

	/**
	 * Recycles and frees underlying Bitmaps.
	 */
	public synchronized void recycle() {
		if (mTextureFront != null) {
			mTextureFront.recycle();
		}
		mTextureFront = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
		mTextureFront.eraseColor(mColorFront);
		if (mTextureBack != null) {
			mTextureBack.recycle();
		}
		mTextureBack = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
		mTextureBack.eraseColor(mColorBack);
		mTexturesChanged = false;
	}

	/**
	 * Resets this CurlPage into its initial state.
	 */
	public synchronized void reset() {
		mColorBack = Color.WHITE;
		mColorFront = Color.WHITE;
		recycle();
	}

	/**
	 * Setter blend color.
	 */
	public synchronized void setColor(int color, int side) {
		switch (side) {
		case SIDE_FRONT:
			mColorFront = color;
			break;
		case SIDE_BACK:
			mColorBack = color;
			break;
		default:
			mColorFront = mColorBack = color;
			break;
		}
	}

	/**
	 * Setter for textures.
	 */
	public synchronized void setTexture(Bitmap texture, int side) {
		if (texture == null) {
			texture = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
			if (side == SIDE_BACK) {
				texture.eraseColor(mColorBack);
			} else {
				texture.eraseColor(mColorFront);
			}
		}
		switch (side) {
		case SIDE_FRONT:
			if (mTextureFront != null)
				mTextureFront.recycle();
			mTextureFront = texture;
			break;
		case SIDE_BACK:
			if (mTextureBack != null)
				mTextureBack.recycle();
			mTextureBack = texture;
			break;
		case SIDE_BOTH:
			if (mTextureFront != null)
				mTextureFront.recycle();
			if (mTextureBack != null)
				mTextureBack.recycle();
			mTextureFront = mTextureBack = texture;
			break;
		}
		mTexturesChanged = true;
	}

}
