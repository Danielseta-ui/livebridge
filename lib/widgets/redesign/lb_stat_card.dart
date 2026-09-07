import 'package:flutter/material.dart';

import '../../theme/livebridge_tokens.dart';
import 'lb_icon.dart';

class LbStatCard extends StatelessWidget {
  const LbStatCard({
    super.key,
    required this.icon,
    required this.title,
    required this.subtitle,
    this.isWarning = false,
    this.onTap,
  });

  final LbIconSymbol icon;
  final String title;
  final String subtitle;
  final bool isWarning;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final LbPalette palette = LbPalette.of(context);
    final Color iconColor = isWarning ? palette.warning : palette.accent;
    final Color subtitleColor = isWarning
        ? palette.warning
        : palette.textSecondary;

    return Semantics(
      button: onTap != null,
      child: GestureDetector(
        onTap: onTap,
        behavior: HitTestBehavior.opaque,
        child: AspectRatio(
          aspectRatio: LbSpacing.statCardAspectRatio,
          child: Container(
            padding: const EdgeInsets.all(LbSpacing.statCardPadding),
            decoration: BoxDecoration(
              color: isWarning ? palette.warningSurface : palette.surface,
              borderRadius: BorderRadius.circular(LbRadius.smallCard),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                LbIcon(
                  symbol: icon,
                  size: LbSpacing.statCardIcon,
                  color: iconColor,
                ),
                const SizedBox(height: LbSpacing.statCardIconGap),
                _LbStatCardText(
                  text: title,
                  style: LbTextStyles.statTitle.copyWith(
                    color: palette.textPrimary,
                  ),
                ),
                const SizedBox(height: 2),
                _LbStatCardText(
                  text: subtitle,
                  style: LbTextStyles.statCaption.copyWith(
                    color: subtitleColor,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _LbStatCardText extends StatelessWidget {
  const _LbStatCardText({
    required this.text,
    required this.style,
  });

  final String text;
  final TextStyle style;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: FittedBox(
        fit: BoxFit.scaleDown,
        child: Text(
          text,
          maxLines: 1,
          softWrap: false,
          textAlign: TextAlign.center,
          style: style,
        ),
      ),
    );
  }
}
