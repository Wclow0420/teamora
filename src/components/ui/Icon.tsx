import React from 'react';
import Svg, { Path, Circle, Rect, G } from 'react-native-svg';
import { palette } from '@/theme';

/**
 * Clean 24px stroke icon set, ported 1:1 from the design system. Every icon
 * draws on a 24×24 viewBox with round caps/joins. Colour follows `color`
 * (defaults to ink); fills inherit the stroke colour where needed.
 *
 * Usage: <Icon name="clock" size={20} color={palette.coral} />
 */

export type IconName =
  | 'clock' | 'home' | 'calendar' | 'wallet' | 'user' | 'leave' | 'claim'
  | 'schedule' | 'pin' | 'face' | 'check' | 'bell' | 'download' | 'plus'
  | 'chevR' | 'chevD' | 'chevL' | 'arrowR' | 'sun' | 'coffee' | 'briefcase'
  | 'receipt' | 'fingerprint' | 'shield' | 'trend' | 'gift' | 'minus'
  | 'camera' | 'gear' | 'logout' | 'mail' | 'phone' | 'edit' | 'doc'
  | 'star' | 'x' | 'search' | 'building' | 'megaphone' | 'heart' | 'grid'
  | 'users' | 'filter' | 'dots' | 'sort' | 'export' | 'play' | 'lock';

type Props = {
  name: IconName;
  size?: number;
  color?: string;
  stroke?: number;
};

export function Icon({ name, size = 24, color = palette.ink, stroke = 1.8 }: Props) {
  const p = {
    fill: 'none' as const,
    stroke: color,
    strokeWidth: stroke,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
  };
  const dotFill = { fill: color, stroke: 'none' as const };

  const body = (() => {
    switch (name) {
      case 'clock':
        return <><Circle cx={12} cy={12} r={9} {...p} /><Path d="M12 7.5V12l3 2" {...p} /></>;
      case 'home':
        return <Path d="M4 11l8-6 8 6v8a1 1 0 0 1-1 1h-4v-5h-6v5H5a1 1 0 0 1-1-1z" {...p} />;
      case 'calendar':
        return <><Rect x={4} y={5} width={16} height={15} rx={2.5} {...p} /><Path d="M4 9h16M8 3v4M16 3v4" {...p} /></>;
      case 'wallet':
        return <><Rect x={3} y={6} width={18} height={13} rx={3} {...p} /><Path d="M3 10h18M16.5 14h.01" {...p} /></>;
      case 'user':
        return <><Circle cx={12} cy={8.5} r={3.5} {...p} /><Path d="M5 19.5c.8-3.4 3.4-5 7-5s6.2 1.6 7 5" {...p} /></>;
      case 'leave':
        return <><Path d="M3 13h18M12 13V8M12 8a4 4 0 0 1 8 0M12 8a4 4 0 0 0-8 0" {...p} /><Path d="M5 13l1.2 6.5h11.6L19 13" {...p} /></>;
      case 'claim':
        return <><Path d="M6 3h12v18l-3-2-3 2-3-2-3 2z" {...p} /><Path d="M9 8h6M9 12h6" {...p} /></>;
      case 'schedule':
        return <><Rect x={4} y={5} width={16} height={15} rx={2.5} {...p} /><Path d="M4 10h16M8.5 14h2M8.5 17h5" {...p} /></>;
      case 'pin':
        return <><Path d="M12 21c4-4.5 7-7.6 7-11a7 7 0 1 0-14 0c0 3.4 3 6.5 7 11z" {...p} /><Circle cx={12} cy={10} r={2.6} {...p} /></>;
      case 'face':
        return <><Circle cx={12} cy={12} r={9} {...p} /><Path d="M9 10h.01M15 10h.01M9 14.5c.9.9 2 1.3 3 1.3s2.1-.4 3-1.3" {...p} /></>;
      case 'check':
        return <Path d="M5 12.5l4.5 4.5L19 7.5" {...p} />;
      case 'bell':
        return <><Path d="M6 9a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6z" {...p} /><Path d="M10 19a2 2 0 0 0 4 0" {...p} /></>;
      case 'download':
        return <><Path d="M12 4v11M7.5 11l4.5 4 4.5-4" {...p} /><Path d="M5 19.5h14" {...p} /></>;
      case 'plus':
        return <Path d="M12 5v14M5 12h14" {...p} />;
      case 'chevR':
        return <Path d="M9 6l6 6-6 6" {...p} />;
      case 'chevL':
        return <Path d="M15 6l-6 6 6 6" {...p} />;
      case 'chevD':
        return <Path d="M6 9l6 6 6-6" {...p} />;
      case 'arrowR':
        return <Path d="M5 12h14M13 6l6 6-6 6" {...p} />;
      case 'sun':
        return <><Circle cx={12} cy={12} r={4} {...p} /><Path d="M12 2v2.5M12 19.5V22M2 12h2.5M19.5 12H22M5 5l1.8 1.8M17.2 17.2L19 19M19 5l-1.8 1.8M6.8 17.2L5 19" {...p} /></>;
      case 'coffee':
        return <><Path d="M4 8h13v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5z" {...p} /><Path d="M17 9h2.5a2.5 2.5 0 0 1 0 5H17" {...p} /></>;
      case 'briefcase':
        return <><Rect x={3} y={7} width={18} height={13} rx={2.5} {...p} /><Path d="M8 7V5.5A1.5 1.5 0 0 1 9.5 4h5A1.5 1.5 0 0 1 16 5.5V7M3 12h18" {...p} /></>;
      case 'receipt':
        return <><Path d="M5 3h14v18l-2.3-1.6L14.3 21 12 19.4 9.7 21l-2.4-1.6L5 21z" {...p} /><Path d="M8.5 8h7M8.5 12h7" {...p} /></>;
      case 'fingerprint':
        return <Path d="M12 4a8 8 0 0 0-8 8M20 12a8 8 0 0 0-3-6.2M8 12a4 4 0 0 1 8 0v1M16 13.5c0 3-1 5-1 5M12 11v3c0 3-1 5-2 6M8 14c.5 2 0 4-1 5.5" {...p} />;
      case 'shield':
        return <><Path d="M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6z" {...p} /><Path d="M9 12l2 2 4-4" {...p} /></>;
      case 'trend':
        return <Path d="M4 16l5-5 3 3 7-7M16 7h4v4" {...p} />;
      case 'gift':
        return <><Rect x={4} y={9} width={16} height={11} rx={1.5} {...p} /><Path d="M4 13h16M12 9v11M12 9c-1-3-5-3-5 0M12 9c1-3 5-3 5 0" {...p} /></>;
      case 'minus':
        return <Path d="M5 12h14" {...p} />;
      case 'camera':
        return <><Rect x={3} y={7} width={18} height={13} rx={3} {...p} /><Circle cx={12} cy={13.5} r={3.4} {...p} /><Path d="M8.5 7l1.2-2h4.6L15.5 7" {...p} /></>;
      case 'gear':
        return <><Circle cx={12} cy={12} r={3.2} {...p} /><Path d="M12 3v2.4M12 18.6V21M21 12h-2.4M5.4 12H3M18.4 5.6l-1.7 1.7M7.3 16.7l-1.7 1.7M18.4 18.4l-1.7-1.7M7.3 7.3 5.6 5.6" {...p} /></>;
      case 'logout':
        return <><Path d="M15 5h-3a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h3" {...p} /><Path d="M19 12H10M16 9l3 3-3 3" {...p} /></>;
      case 'mail':
        return <><Rect x={3} y={5.5} width={18} height={13} rx={2.5} {...p} /><Path d="M4.5 7.5l7.5 5.5 7.5-5.5" {...p} /></>;
      case 'phone':
        return <Path d="M6 4h3l1.6 4-2 1.4a11 11 0 0 0 5 5l1.4-2 4 1.6V20a1 1 0 0 1-1.1 1A16 16 0 0 1 4 6.1 1 1 0 0 1 5 5" {...p} />;
      case 'edit':
        return <><Path d="M16.5 4.5l3 3" {...p} /><Path d="M4 20l1-4L16.5 4.5l3 3L8 19z" {...p} /></>;
      case 'doc':
        return <><Path d="M7 3h7l4 4v14H7z" {...p} /><Path d="M14 3v4h4M10 12.5h5M10 16h5" {...p} /></>;
      case 'star':
        return <Path d="M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 17l-5.2 2.6 1-5.8-4.3-4.1 5.9-.9z" {...p} />;
      case 'x':
        return <Path d="M6 6l12 12M18 6 6 18" {...p} />;
      case 'search':
        return <><Circle cx={11} cy={11} r={7} {...p} /><Path d="M20 20l-3.6-3.6" {...p} /></>;
      case 'building':
        return <><Rect x={5} y={3} width={14} height={18} rx={1.5} {...p} /><Path d="M9 7h2M13 7h2M9 11h2M13 11h2M10 21v-3.5h4V21" {...p} /></>;
      case 'megaphone':
        return <><Path d="M4 10v4a1 1 0 0 0 1 1h2.2l7.8 4V5L7.2 9H5a1 1 0 0 0-1 1z" {...p} /><Path d="M18 9.2a3 3 0 0 1 0 5.6" {...p} /></>;
      case 'heart':
        return <Path d="M12 20s-7-4.4-7-9.4A3.6 3.6 0 0 1 12 8a3.6 3.6 0 0 1 7-2.4c0 5-7 14.4-7 14.4z" {...p} />;
      case 'grid':
        return <><Rect x={3.5} y={3.5} width={7} height={7} rx={1.8} {...p} /><Rect x={13.5} y={3.5} width={7} height={7} rx={1.8} {...p} /><Rect x={3.5} y={13.5} width={7} height={7} rx={1.8} {...p} /><Rect x={13.5} y={13.5} width={7} height={7} rx={1.8} {...p} /></>;
      case 'users':
        return <><Circle cx={9} cy={8.5} r={3.2} {...p} /><Path d="M3.5 19c.7-3 2.9-4.5 5.5-4.5S13.8 16 14.5 19" {...p} /><Path d="M16 5.5a3 3 0 0 1 0 6M17.5 14.6c2 .6 3.4 2 3.9 4.4" {...p} /></>;
      case 'filter':
        return <Path d="M4 5h16l-6.4 7.6V19l-3.2 1.6V12.6z" {...p} />;
      case 'dots':
        return <><Circle cx={12} cy={5} r={1.4} {...dotFill} /><Circle cx={12} cy={12} r={1.4} {...dotFill} /><Circle cx={12} cy={19} r={1.4} {...dotFill} /></>;
      case 'sort':
        return <Path d="M8 5v14M8 5 4.5 8.5M8 5l3.5 3.5M16 19V5M16 19l3.5-3.5M16 19l-3.5-3.5" {...p} />;
      case 'export':
        return <><Path d="M12 15V4M8.5 7.5 12 4l3.5 3.5" {...p} /><Path d="M5 14v4.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V14" {...p} /></>;
      case 'play':
        return <Path d="M7 5l11 7-11 7z" {...{ ...p, fill: color }} />;
      case 'lock':
        return <><Rect x={5} y={11} width={14} height={9} rx={2.5} {...p} /><Path d="M8 11V8a4 4 0 0 1 8 0v3" {...p} /></>;
      default:
        return null;
    }
  })();

  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <G>{body}</G>
    </Svg>
  );
}
