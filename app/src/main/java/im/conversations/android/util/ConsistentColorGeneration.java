/*
 * Copyright 2019-2021 Daniel Gultsch
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.conversations.android.util;

import android.content.Context;
import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import com.google.android.material.color.MaterialColors;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import org.hsluv.HUSLColorConverter;
import org.jxmpp.jid.Jid;

public final class ConsistentColorGeneration {

    private ConsistentColorGeneration() {
        throw new IllegalStateException("This is a Utility class");
    }

    @SuppressWarnings("deprecation")
    private static double angle(final String input) {
        final byte[] digest = Hashing.sha1().hashString(input, Charsets.UTF_8).asBytes();
        final int angle = ((int) (digest[0]) & 0xff) + ((int) (digest[1]) & 0xff) * 256;
        return angle / 65536.0;
    }

    @ColorInt
    public static int rgb(final String input) {
        final double[] rgb =
                HUSLColorConverter.hsluvToRgb(new double[] {angle(input) * 360, 85, 58});
        return rgb(
                (int) Math.round(rgb[0] * 255),
                (int) Math.round(rgb[1] * 255),
                (int) Math.round(rgb[2] * 255));
    }

    @ColorInt
    public static int harmonized(final Context context, final String input) {
        return MaterialColors.harmonizeWithPrimary(context, rgb(input));
    }

    public static int harmonized(final Context context, final Jid jid) {
        return harmonized(context, jid.toString());
    }

    @ColorInt
    private static int rgb(
            @IntRange(from = 0, to = 255) int red,
            @IntRange(from = 0, to = 255) int green,
            @IntRange(from = 0, to = 255) int blue) {
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }
}
