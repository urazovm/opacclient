/**
 * Copyright (C) 2013 by Raphael Michel under the MIT license:
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the Software 
 * is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, 
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, 
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 */
package de.geeksfactory.opacclient.apis;

import java.io.IOException;

import org.json.JSONException;

import android.net.Uri;
import de.geeksfactory.opacclient.objects.Account;
import de.geeksfactory.opacclient.objects.AccountData;
import de.geeksfactory.opacclient.objects.DetailledItem;

/**
 * If an {@link OpacApi} implementation also implements this interface, the
 * library can be used to download ebooks directly inside the app. This is
 * currently NOT implemented or used in the open source version and is more like
 * a bridge between the "Community Edition" and the "Plus Edition" of the App.
 * 
 * @author Raphael Michel
 */
public interface EbookServiceApi {

	public AccountData account(Account account) throws IOException,
			JSONException;

	/**
	 * The result of a {@link #booking(String, Account, int, String)} call. The
	 * structure of the call and response is similar to
	 * {@link OpacApi#reservation(String, Account, int, String)}.
	 */
	public class BookingResult extends OpacApi.MultiStepResult {

		public BookingResult(Status status) {
			super(status);
		}
		
		public BookingResult(Status status, String message) {
			super(status, message);
		}
	}

	/**
	 * Book an electronical item identified by booking_info to the users
	 * account. booking_info is what you returned in your DetailledItem object
	 * in your getResult hook.
	 */
	public BookingResult booking(String booking_info, Account account,
			int useraction, String selection) throws IOException;

	/**
	 * Is this a supported downloadable ebook? May not do network requests.
	 */
	public boolean isEbook(DetailledItem item);

	/**
	 * Download the item identified by item_id. Returns an URL where the item
	 * can be downloaded from.
	 */
	public Uri downloadItem(Account account, String item_id);
}
