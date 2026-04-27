package uk.gov.hmcts.reform.migration.reimpl;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

public class TestUtils {

    private TestUtils() {
    }

    public static Matcher<Object> jsonArrayWith(final Long nextCase) {
        return new BaseMatcher<>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("JSON array containing only: " + nextCase + " (i.e. <[" + nextCase + "]>)");
            }

            @Override
            public boolean matches(Object o) {
                if (!(o instanceof JSONArray)) {
                    return false;
                }

                final JSONArray jsonArray = (JSONArray) o;
                if (jsonArray.length() != 1) {
                    return false;
                }

                final Object entry = jsonArray.get(0);
                if (!(entry instanceof Long)) {
                    return false;
                }

                final Long entryValue = (Long) entry;
                if (!entryValue.equals(nextCase)) {
                    return false;
                }
                return true;
            }
        };
    }

    public static Matcher<Object> hasFilterForDate(final LocalDate localDate) {
        return new BaseMatcher<>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("JSON object with an elasticsearch filter for last_modified after ")
                    .appendText(localDate.toString());
            }

            @Override
            public boolean matches(Object o) {
                if (!(o instanceof JSONObject)) {
                    return false;
                }
                final JSONObject jsonObject = (JSONObject) o;
                final JSONObject query = jsonObject.optJSONObject("query");
                if (query == null) {
                    return false;
                }
                final JSONObject bool = query.optJSONObject("bool");
                if (bool == null) {
                    return false;
                }
                final JSONArray filter = bool.optJSONArray("filter");
                if (filter == null) {
                    return false;
                }
                for (int i = 0; i < filter.length(); i++) {
                    final JSONObject filterEntry = filter.optJSONObject(i);
                    if (filterEntry == null) {
                        continue;
                    }

                    final JSONObject range = filterEntry.optJSONObject("range");
                    if (range == null) {
                        continue;
                    }

                    final JSONObject lastModified = range.optJSONObject("last_modified");
                    if (lastModified == null) {
                        continue;
                    }

                    final String gte = lastModified.optString("gte");
                    if (gte == null) {
                        continue;
                    }
                    return gte.equals(localDate.toString());
                }
                return false;
            }
        };
    }
}
