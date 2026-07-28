import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { ImdbId, imdbUrl } from '../../core/domain';
import { injectTitleMeta } from '../../core/title-meta';
import { UserPrefsStore } from '../../core/user-prefs-store';
import { AgeBadge } from '../age-badge/age-badge';
import { PosterThumb } from '../poster-thumb/poster-thumb';

/**
 * A table title cell: the poster thumbnail, the title link (in German when the user's German-title
 * preference is on and a German title exists, otherwise the original name), and the FSK age badge.
 * The per-title metadata (rating + German title) is fetched **once** from {@code /api/titles/{id}/meta}
 * — and only while at least one of those two preferences is on, so it costs nothing when both are off.
 */
@Component({
  selector: 'app-title-cell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PosterThumb, AgeBadge],
  template: `
    <div class="title-cell">
      <app-poster-thumb [imdbId]="imdbId()" [name]="name()" />
      <a [href]="imdbUrl(imdbId())" target="_blank" rel="noopener">{{ displayTitle() }}</a>
      @if (userPrefsStore.showAgeRatings() && meta()?.rating; as rating) {
        <app-age-badge [rating]="rating" />
      }
    </div>
  `,
})
export class TitleCell {
  readonly imdbId = input.required<ImdbId>();
  readonly name = input<string>('');

  protected readonly userPrefsStore = inject(UserPrefsStore);
  protected readonly imdbUrl = imdbUrl;
  protected readonly meta = injectTitleMeta(() => this.imdbId());

  /** The German title when the preference is on and one exists, else the original (English) name. */
  protected readonly displayTitle = computed(
    () => (this.userPrefsStore.showGermanTitle() && this.meta()?.germanTitle) || this.name(),
  );
}
