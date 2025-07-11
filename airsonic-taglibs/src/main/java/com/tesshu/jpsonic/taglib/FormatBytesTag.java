/*
 * This file is part of Jpsonic.
 *
 * Jpsonic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Jpsonic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * (C) 2009 Sindre Mehus
 * (C) 2016 Airsonic Authors
 * (C) 2018 tesshucom
 */

package com.tesshu.jpsonic.taglib;

import java.io.IOException;
import java.util.Locale;

import com.tesshu.jpsonic.util.StringUtilBase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspTagException;
import jakarta.servlet.jsp.tagext.BodyTagSupport;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Converts a byte-count to a formatted string suitable for display to the user,
 * with respect to the current locale.
 * <p/>
 * For instance:
 * <ul>
 * <li><code>format(918)</code> returns <em>"918 B"</em>.</li>
 * <li><code>format(98765)</code> returns <em>"96 KB"</em>.</li>
 * <li><code>format(1238476)</code> returns <em>"1.2 MB"</em>.</li>
 * </ul>
 * This class assumes that 1 KB is 1024 bytes.
 *
 * @author Sindre Mehus
 */
@SuppressWarnings("serial")
public class FormatBytesTag extends BodyTagSupport {

    private long bytes;

    @Override
    public int doStartTag() {
        return EVAL_BODY_BUFFERED;
    }

    @Override
    public int doEndTag() throws JspException {
        Locale locale = RequestContextUtils
            .getLocale((HttpServletRequest) pageContext.getRequest());
        String result = StringUtilBase.formatBytes(bytes, locale);

        try {
            pageContext.getOut().print(result);
        } catch (IOException x) {
            throw new JspTagException(x);
        }
        return EVAL_PAGE;
    }

    @Override
    public void release() {
        bytes = 0L;
        super.release();
    }

    public long getBytes() {
        return bytes;
    }

    public void setBytes(long bytes) {
        this.bytes = bytes;
    }
}
