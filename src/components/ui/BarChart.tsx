import React from 'react';
import { View, Text } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { palette, radius, font, gradients } from '@/theme';

type Props = {
  /** Bar values. */
  data: number[];
  /** Max value the bars scale against. */
  max: number;
  /** X-axis labels (one per bar). */
  labels: string[];
  /** Indices that should render as the coral gradient (e.g. today). Others use the muted tan. */
  highlight?: number[];
  /** Indices to render flat as a "no data" track. */
  empty?: number[];
  height?: number;
  barColor?: string;
};

/** Weekly attendance/hours bar chart — coral gradient highlight on the tan track. */
export function BarChart({ data, max, labels, highlight = [], empty = [], height = 60, barColor = '#EAD2BE' }: Props) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: 7, marginTop: 14 }}>
      {data.map((v, i) => {
        const isHi = highlight.includes(i);
        const isEmpty = empty.includes(i) || v === 0;
        const h = Math.max(0, (v / max) * 100);
        return (
          <View key={i} style={{ flex: 1, alignItems: 'center', gap: 7 }}>
            <View style={{ width: '100%', height, justifyContent: 'flex-end' }}>
              {isHi && !isEmpty ? (
                <LinearGradient colors={gradients.coral} start={{ x: 0, y: 0 }} end={{ x: 0, y: 1 }} style={{ width: '100%', height: `${h}%`, borderRadius: 6 }} />
              ) : (
                <View style={{ width: '100%', height: `${isEmpty ? 6 : h}%`, borderRadius: 6, backgroundColor: isEmpty ? palette.line : barColor }} />
              )}
            </View>
            <Text style={[font(600), { fontSize: 10, color: isHi ? palette.coral : palette.faint }]}>{labels[i]}</Text>
          </View>
        );
      })}
    </View>
  );
}
