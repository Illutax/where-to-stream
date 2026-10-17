#!/usr/bin/env bash
#
# notify.sh — ntfy notifications for the nightly update run.
#
# Sourced by cron.sh; run directly to check the setup without touching the deployment:
#
#     ./notify.sh --test            one probe notification — does the configuration reach the topic
#     ./notify.sh --test-events     one of each notification the nightly run can actually send
#
# The interface is deliberately the one the pihole-update job on this host already uses — same
# function signature, same variable names, same meanings — so neither script has to be re-read to
# understand the other:
#
#     notify <title> <message> [priority] [tags]
#
# What each event looks like lives here, in notify_deployed/notify_nothing_new/notify_failed,
# rather than at the call sites in cron.sh. That is what makes --test-events worth anything: it
# fires the production messages themselves, so a drill cannot drift away from the real thing while
# still passing.
#
# Secrets and local overrides, not in Git:
#
#     ENV_FILE="${ENV_FILE:-$HOME/.config/w2s-update.env}"
#
#     NTFY_URL=https://ntfy.example.net/my-topic
#     NTFY_TOKEN=tk_...
#
# NTFY_URL is the full topic URL and, as in pihole-update.sh, empty means "do not notify" rather
# than "fall back to something" — a host that was never configured stays quiet instead of guessing
# where to publish. Which server and which topic is deployment configuration and stays in that
# file: the address of a piece of home infrastructure is not something a public repository should
# carry, and a topic URL is itself close to a credential on a server that allows anonymous
# publishing. NTFY_TOKEN is the token of a user with write access to the topic; `chmod 600` the
# file, nothing in this repository is a place to keep one.
#
# Nothing in here may fail the update: a notifier that is down, misconfigured or slow must not keep
# a deployment from happening, so every path returns 0 and curl is bounded by a timeout.

# Secrets & local overrides (NTFY_URL, NTFY_TOKEN, ...) – not in Git
ENV_FILE="${ENV_FILE:-$HOME/.config/w2s-update.env}"
# shellcheck disable=SC1090
[[ -r "$ENV_FILE" ]] && source "$ENV_FILE"

NTFY_URL="${NTFY_URL:-}"      # optional, full topic URL, e.g. https://ntfy.sh/my-topic
NTFY_TOKEN="${NTFY_TOKEN:-}"  # token of a user with write access to the topic

# Set by --test-events so a drill cannot be mistaken for a real 4 a.m. alert. Empty in production.
NOTIFY_TITLE_PREFIX="${NOTIFY_TITLE_PREFIX:-}"

notify() {
    local title="${NOTIFY_TITLE_PREFIX}$1" msg="$2" prio="${3:-default}" tags="${4:-}"
    [[ -n "$NTFY_URL" ]] || return 0
    local -a auth=()
    [[ -n "$NTFY_TOKEN" ]] && auth=(-H "Authorization: Bearer $NTFY_TOKEN")
    # Body on stdin rather than -d "$msg": curl reads a *file* when the argument starts with '@',
    # and the failure notification carries a tail of the run log, which can begin with anything.
    printf '%s' "$msg" | curl -fsS --max-time 10 "${auth[@]}" \
        -H "Title: $title" -H "Priority: $prio" -H "Tags: $tags" \
        --data-binary @- "$NTFY_URL" >/dev/null 2>&1 || echo "ntfy notification failed" >&2
    return 0
}

# ---------------------------------------------------------------- The events cron.sh can report
head_line() { # one line identifying what is deployed now
    git log -1 --format='%h %s' 2>/dev/null || echo "unknown commit"
}

notify_deployed() { # previous commit, Spring Boot status
    notify "w2s deployed" "$(head_line)
Previously: $1
$2" low white_check_mark
}

notify_nothing_new() { # current commit, Spring Boot status
    notify "w2s update ran" "Nothing new to deploy ($1). $2" min +1
}

notify_failed() { # step, tail of the run log
    notify "w2s update failed" "$1 failed on $(hostname).

$2" high rotating_light
}

# ---------------------------------------------------------------- Run directly: check, change nothing
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    [[ -n "$NTFY_URL" ]] || { echo "NTFY_URL is not set (ENV_FILE: $ENV_FILE)" >&2; exit 1; }
    case "${1:-}" in
        --test)
            notify "w2s update: test" "Notification works. HEAD: $(head_line)" low test_tube
            ;;
        --test-events)
            # The real functions with stand-in data, so what arrives is what a real run would send
            # -- only the titles are marked, and the deployment is not touched.
            NOTIFY_TITLE_PREFIX="[drill] "
            notify_deployed "0000000" "Spring Boot updated."
            notify_nothing_new "$(git rev-parse --short HEAD 2>/dev/null || echo unknown)" "No new Spring Boot version."
            notify_failed "Application update" "    | this is a drill, no deployment was attempted
    | the real notification carries the last 20 lines of the run log here"
            ;;
        *)
            echo "Usage: source notify.sh   |   $0 --test   |   $0 --test-events" >&2
            exit 64
            ;;
    esac
fi
