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
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.acra.ACRA;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import de.geeksfactory.opacclient.NotReachableException;
import de.geeksfactory.opacclient.objects.Account;
import de.geeksfactory.opacclient.objects.AccountData;
import de.geeksfactory.opacclient.objects.Detail;
import de.geeksfactory.opacclient.objects.DetailledItem;
import de.geeksfactory.opacclient.objects.Filter;
import de.geeksfactory.opacclient.objects.Filter.Option;
import de.geeksfactory.opacclient.objects.Library;
import de.geeksfactory.opacclient.objects.SearchRequestResult;
import de.geeksfactory.opacclient.objects.SearchResult;
import de.geeksfactory.opacclient.objects.SearchResult.MediaType;
import de.geeksfactory.opacclient.storage.MetaDataSource;

/**
 * API für Web-Opacs von Zones mit dem Hinweis "Zones.2.2.45.04" im Footer.
 * Einziger bekannter Einsatzort ist Hamburg.
 * 
 * TODO: Suche nach Medientypen, alles mit Konten + Vorbestellen
 * 
 */
public class Zones22 extends BaseApi {

	private String opac_url = "";
	private JSONObject data;
	private MetaDataSource metadata;
	private String last_error;
	private Library library;
	private int page;
	private String searchobj;

	private static HashMap<String, MediaType> defaulttypes = new HashMap<String, MediaType>();
	static {
		defaulttypes.put("Buch", MediaType.BOOK);
		defaulttypes.put("Buch/Druckschrift", MediaType.BOOK);
		defaulttypes.put("Buch Erwachsene", MediaType.BOOK);
		defaulttypes.put("Buch Kinder/Jugendliche", MediaType.BOOK);
		defaulttypes.put("Kinder-Buch", MediaType.BOOK);
		defaulttypes.put("DVD", MediaType.DVD);
		defaulttypes.put("Kinder-DVD", MediaType.DVD);
		defaulttypes.put("Konsolenspiele", MediaType.GAME_CONSOLE);
		defaulttypes.put("Blu-ray Disc", MediaType.BLURAY);
		defaulttypes.put("Compact Disc", MediaType.CD);
		defaulttypes.put("CD-ROM", MediaType.CD_SOFTWARE);
		defaulttypes.put("Kinder-CD", MediaType.CD_SOFTWARE);
		defaulttypes.put("Noten", MediaType.SCORE_MUSIC);
		defaulttypes.put("Zeitschrift, Heft", MediaType.MAGAZINE);
		defaulttypes.put("E-Book", MediaType.EBOOK);
		defaulttypes.put("CDROM", MediaType.CD_SOFTWARE);
		defaulttypes.put("E-Audio", MediaType.MP3);
		defaulttypes.put("CD", MediaType.CD);
	}

	@Override
	public String[] getSearchFields() {
		return new String[] { KEY_SEARCH_QUERY_TITLE, KEY_SEARCH_QUERY_AUTHOR,
				KEY_SEARCH_QUERY_KEYWORDA, KEY_SEARCH_QUERY_BRANCH,
				KEY_SEARCH_QUERY_ISBN, KEY_SEARCH_QUERY_YEAR };
	}

	@Override
	public String getLast_error() {
		return last_error;
	}

	public void extract_meta(Document doc) {
		// Zweigstellen auslesen
		Elements zst_opts = doc.select(".TabRechAv .limitChoice label");
		metadata.open();
		metadata.clearMeta(library.getIdent());
		for (int i = 0; i < zst_opts.size(); i++) {
			Element opt = zst_opts.get(i);
			metadata.addMeta(MetaDataSource.META_TYPE_BRANCH,
					library.getIdent(), opt.attr("for"), opt.text().trim());
		}

		metadata.close();
	}

	@Override
	public void start() throws ClientProtocolException, SocketException,
			IOException, NotReachableException {
		String html = httpGet(opac_url
				+ "/APS_ZONES?fn=AdvancedSearch&Style=Portal3&SubStyle=&Lang=GER&ResponseEncoding=utf-8");

		Document doc = Jsoup.parse(html);

		searchobj = doc.select("#ExpertSearch").attr("action");

		metadata.open();
		if (!metadata.hasMeta(library.getIdent())) {
			metadata.close();
			extract_meta(doc);
		} else {
			metadata.close();
		}
	}

	@Override
	public void init(MetaDataSource metadata, Library lib) {
		super.init(metadata, lib);

		this.metadata = metadata;
		this.library = lib;
		this.data = lib.getData();

		try {
			this.opac_url = data.getString("baseurl");
		} catch (JSONException e) {
			ACRA.getErrorReporter().handleException(e);
		}
	}

	public static String getStringFromBundle(Bundle bundle, String key) {
		// Workaround for Bundle.getString(key, default) being available not
		// before API 12
		String res = bundle.getString(key);
		if (res == null)
			res = "";
		return res;
	}

	private int addParameters(Bundle query, String key, String searchkey,
			List<NameValuePair> params, int index) {
		if (!query.containsKey(key) || query.getString(key).equals(""))
			return index;

		if (index != 1)
			params.add(new BasicNameValuePair(".form.t" + index + ".logic",
					"and"));
		params.add(new BasicNameValuePair("q.form.t" + index + ".term",
				searchkey));
		params.add(new BasicNameValuePair("q.form.t" + index + ".expr", query
				.getString(key)));
		return index + 1;

	}

	@Override
	public SearchRequestResult search(Bundle query) throws IOException,
			NotReachableException {
		start();

		List<NameValuePair> params = new ArrayList<NameValuePair>();

		params.add(new BasicNameValuePair("Style", "Portal3"));
		params.add(new BasicNameValuePair("SubStyle", ""));
		params.add(new BasicNameValuePair("Lang", "GER"));
		params.add(new BasicNameValuePair("ResponseEncoding", "utf-8"));
		params.add(new BasicNameValuePair("Method", "QueryWithLimits"));
		params.add(new BasicNameValuePair("SearchType", "AdvancedSearch"));
		params.add(new BasicNameValuePair("TargetSearchType", "AdvancedSearch"));
		params.add(new BasicNameValuePair("DB", "SearchServer"));
		params.add(new BasicNameValuePair("q.PageSize", "10"));

		int index = 1;

		index = addParameters(query, KEY_SEARCH_QUERY_TITLE, "ti=", params,
				index);
		index = addParameters(query, KEY_SEARCH_QUERY_AUTHOR, "au=", params,
				index);
		index = addParameters(query, KEY_SEARCH_QUERY_ISBN, "sb=", params,
				index);
		index = addParameters(query, KEY_SEARCH_QUERY_KEYWORDA, "su=", params,
				index);
		index = addParameters(query, KEY_SEARCH_QUERY_YEAR, "dp=", params,
				index);

		if (query.containsKey(KEY_SEARCH_QUERY_BRANCH)
				&& !query.getString(KEY_SEARCH_QUERY_BRANCH).equals(""))
			params.add(new BasicNameValuePair("q.limits.limit", query
					.getString(KEY_SEARCH_QUERY_BRANCH)));

		if (index > 3) {
			last_error = "Diese Bibliothek unterstützt nur bis zu vier benutzte Suchkriterien.";
			return null;
		} else if (index == 1) {
			last_error = "Es wurden keine Suchkriterien eingegeben.";
			return null;
		}

		String html = httpGet(opac_url + "/" + searchobj + "?"
				+ URLEncodedUtils.format(params, "UTF-8"));

		page = 1;

		return parse_search(html, page);
	}

	@Override
	public SearchRequestResult searchGetPage(int page) throws IOException,
			NotReachableException {
		List<NameValuePair> params = new ArrayList<NameValuePair>();

		params.add(new BasicNameValuePair("Style", "Portal3"));
		params.add(new BasicNameValuePair("SubStyle", ""));
		params.add(new BasicNameValuePair("Lang", "GER"));
		params.add(new BasicNameValuePair("ResponseEncoding", "utf-8"));
		if (page > this.page) {
			params.add(new BasicNameValuePair("Method", "PageDown"));
		} else {
			params.add(new BasicNameValuePair("Method", "PageUp"));
		}
		params.add(new BasicNameValuePair("PageSize", "10"));

		String html = httpGet(opac_url + "/" + searchobj + "?"
				+ URLEncodedUtils.format(params, "UTF-8"));
		this.page = page;

		return parse_search(html, page);
	}

	private SearchRequestResult parse_search(String html, int page) {
		Document doc = Jsoup.parse(html);
		doc.setBaseUri(opac_url + "/APS_PRESENT_BIB");

		if (doc.select("#ErrorAdviceRow").size() > 0) {
			last_error = doc.select("#ErrorAdviceRow").text().trim();
			return null;
		}

		int results_total = -1;

		if (doc.select(".searchHits").size() > 0) {
			results_total = Integer.parseInt(doc.select(".searchHits").first()
					.text().trim().replaceAll(".*\\(([0-9]+)\\).*", "$1"));
		}

		if (doc.select(".pageNavLink").size() > 0) {
			searchobj = doc.select(".pageNavLink").first().attr("href")
					.split("\\?")[0];
		}

		Elements table = doc.select("#BrowseList > tbody > tr");
		List<SearchResult> results = new ArrayList<SearchResult>();
		for (int i = 0; i < table.size(); i++) {
			Element tr = table.get(i);
			SearchResult sr = new SearchResult();

			String typetext = tr.select(".SummaryMaterialTypeField").text()
					.replace("\n", " ").trim();
			if (data.has("mediatypes")) {
				try {
					sr.setType(MediaType.valueOf(data.getJSONObject(
							"mediatypes").getString(typetext)));
				} catch (JSONException e) {
					sr.setType(defaulttypes.get(typetext));
				} catch (IllegalArgumentException e) {
					sr.setType(defaulttypes.get(typetext));
				}
			} else {
				sr.setType(defaulttypes.get(typetext));
			}

			String desc = "";
			Elements children = tr
					.select(".SummaryDataCell tr, .SummaryDataCellStripe tr");
			int childrennum = children.size();
			boolean haslink = false;

			for (int ch = 0; ch < childrennum; ch++) {
				Element node = children.get(ch);
				if (node.select(".SummaryFieldLegend").text().equals("Titel")) {
					desc += "<b>"
							+ node.select(".SummaryFieldData").text().trim()
							+ "</b><br />";

				} else if (node.select(".SummaryFieldLegend").text()
						.equals("Verfasser")
						|| node.select(".SummaryFieldLegend").text()
								.equals("Jahr")) {
					desc += node.select(".SummaryFieldData").text().trim()
							+ "<br />";
				}

				if (node.select(".SummaryFieldData a.SummaryFieldLink").size() > 0
						&& haslink == false) {
					sr.setId(Uri.parse(
							node.select(".SummaryFieldData a.SummaryFieldLink")
									.attr("abs:href")).getQueryParameter("no"));
					haslink = true;
				}
			}
			if (desc.endsWith("<br />"))
				desc = desc.substring(0, desc.length() - 6);
			sr.setInnerhtml(desc);
			sr.setNr(i);

			results.add(sr);
		}

		return new SearchRequestResult(results, results_total, page);
	}

	@Override
	public DetailledItem getResultById(String id, String homebranch)
			throws IOException, NotReachableException {

		List<NameValuePair> params = new ArrayList<NameValuePair>();

		params.add(new BasicNameValuePair("Style", "Portal3"));
		params.add(new BasicNameValuePair("SubStyle", ""));
		params.add(new BasicNameValuePair("Lang", "GER"));
		params.add(new BasicNameValuePair("ResponseEncoding", "utf-8"));
		params.add(new BasicNameValuePair("no", id));

		String html = httpGet(opac_url + "/APS_PRESENT_BIB?"
				+ URLEncodedUtils.format(params, "UTF-8"));

		return parse_result(id, html);
	}

	@Override
	public DetailledItem getResult(int nr) throws IOException {
		return null;
	}

	private DetailledItem parse_result(String id, String html)
			throws IOException {
		Document doc = Jsoup.parse(html);

		DetailledItem result = new DetailledItem();
		result.setTitle("");
		boolean title_is_set = false;

		result.setId(id);

		Elements detaildiv = doc.select("div.record-item-new");

		Elements detailtrs1 = doc
				.select(".DetailDataCell table table:not(.inRecordHeader) tr");
		for (int i = 0; i < detailtrs1.size(); i++) {
			Element tr = detailtrs1.get(i);
			int s = tr.children().size();
			if (tr.child(0).text().trim().equals("Titel") && !title_is_set) {
				result.setTitle(tr.child(s - 1).text().trim());
				title_is_set = true;
			} else if (s > 1) {
				Element valchild = tr.child(s - 1);
				if (valchild.select("table").isEmpty()) {
					String val = valchild.text().trim();
					if (val.length() > 0)
						result.addDetail(new Detail(tr.child(0).text().trim(),
								val));
				}
			}
		}

		if (!detaildiv.isEmpty()) {
			for (int i = 0; i < detaildiv.size(); i++) {
				Element dd = detaildiv.get(i);
				String text = "";
				for (Node node : dd.childNodes()) {
					if (node instanceof TextNode) {
						String snip = ((TextNode) node).text();
						if (snip.length() > 0)
							text += snip;
					} else if (node instanceof Element) {
						if (((Element) node).tagName().equals("br"))
							text += "\n";
						else {
							String snip = ((Element) node).text().trim();
							if (snip.length() > 0)
								text += snip;
						}
					}
				}
				result.addDetail(new Detail("", text));
			}
		}

		if (doc.select("span.z3988").size() > 0) {
			// Sometimes there is a <span class="Z3988"> item which provides
			// data in a standardized format.
			String z3988data = doc.select("span.z3988").first().attr("title")
					.trim();
			for (String pair : z3988data.split("\\&")) {
				String[] nv = pair.split("=", 2);
				if (nv.length == 2) {
					if (!nv[1].trim().equals("")) {
						if (nv[0].equals("rft.btitle")
								&& result.getTitle().length() == 0) {
							result.setTitle(nv[1]);
						} else if (nv[0].equals("rft.atitle")
								&& result.getTitle().length() == 0) {
							result.setTitle(nv[1]);
						} else if (nv[0].equals("rft.au")) {
							result.addDetail(new Detail("Author", nv[1]));
						}
					}
				}
			}
		}

		Elements copydivs = doc.select(".DetailDataCell div[id^=stock_]");
		String pop = "";
		for (int i = 0; i < copydivs.size(); i++) {
			Element div = copydivs.get(i);

			if (div.attr("id").startsWith("stock_head")) {
				pop = div.text().trim();
				continue;
			}

			ContentValues copy = new ContentValues();

			// This is getting very ugly - check if it is valid for libraries
			// which are not
			// Hamburg.
			int j = 0;
			for (Node node : div.childNodes()) {
				try {
					if (node instanceof Element) {
						if (((Element) node).tag().getName().equals("br")) {
							copy.put(DetailledItem.KEY_COPY_BRANCH, pop);
							result.addCopy(copy);
							j = -1;
						} else if (((Element) node).tag().getName().equals("b")
								&& j == 1) {
							copy.put(DetailledItem.KEY_COPY_LOCATION,
									((Element) node).text());
						} else if (((Element) node).tag().getName().equals("b")
								&& j > 1) {
							copy.put(DetailledItem.KEY_COPY_STATUS,
									((Element) node).text());
						}
						j++;
					} else if (node instanceof TextNode) {
						if (j == 0)
							copy.put(DetailledItem.KEY_COPY_DEPARTMENT,
									((TextNode) node).text());
						if (j == 2)
							copy.put(DetailledItem.KEY_COPY_BARCODE,
									((TextNode) node).getWholeText().trim()
											.split("\n")[0].trim());
						if (j == 6) {
							String text = ((TextNode) node).text().trim();
							copy.put(DetailledItem.KEY_COPY_RETURN,
									text.substring(text.length() - 10));
						}
						j++;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return result;
	}

	// No account support for now.

	@Override
	public ReservationResult reservation(String reservation_info, Account acc,
			int useraction, String selection) throws IOException {
		return new ReservationResult(ReservationResult.Status.UNSUPPORTED);
	}

	@Override
	public ProlongResult prolong(String media, Account account, int useraction,
			String Selection) throws IOException {
		return new ProlongResult(MultiStepResult.Status.UNSUPPORTED);
	}

	@Override
	public boolean cancel(Account account, String a) throws IOException,
			NotReachableException {
		return false;
	}

	@Override
	public AccountData account(Account acc) throws IOException,
			NotReachableException, JSONException, SocketException {
		return null;
	}

	@Override
	public boolean isAccountSupported(Library library) {
		return false;
	}

	@Override
	public boolean isAccountExtendable() {
		return false;
	}

	@Override
	public String getAccountExtendableInfo(Account acc)
			throws ClientProtocolException, SocketException, IOException,
			NotReachableException {
		return null;
	}

	@Override
	public String getShareUrl(String id, String title) {
		List<NameValuePair> params = new ArrayList<NameValuePair>();

		params.add(new BasicNameValuePair("Style", "Portal3"));
		params.add(new BasicNameValuePair("SubStyle", ""));
		params.add(new BasicNameValuePair("Lang", "GER"));
		params.add(new BasicNameValuePair("ResponseEncoding", "utf-8"));
		params.add(new BasicNameValuePair("no", id));

		return opac_url + "/APS_PRESENT_BIB?"
				+ URLEncodedUtils.format(params, "UTF-8");
	}

	@Override
	public int getSupportFlags() {
		return 0;
	}

	@Override
	public boolean prolongAll(Account account) throws IOException {
		return false;
	}

	@Override
	public SearchRequestResult filterResults(Filter filter, Option option) {
		// TODO Auto-generated method stub
		return null;
	}
}
