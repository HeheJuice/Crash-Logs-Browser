#!/system/bin/sh
# tree-note.sh — recursive POSIX sh version (Android mksh / toybox friendly)
# Walks every folder down the tree and writes results to tree-note.txt.

DIR="$(cd "$(dirname "$0")" && pwd)"
NAME="$(basename "$DIR")"
OUT="$DIR/tree-note.txt"

# --- collect all directories, relative to $DIR, sorted ---
dirs_tmp="$(mktemp)"
find "$DIR" -mindepth 1 -type d | LC_ALL=C sort > "$dirs_tmp"

# --- longest folder label for alignment ---
maxlen=0
while IFS= read -r d; do
    rel="${d#$DIR/}"
    label="$(basename "$d")/"
    indent=""
    depth=$(printf '%s' "$rel" | awk -F/ '{print NF-1}')
    # indent per depth level (2 spaces each)
    i=0
    while [ "$i" -lt "$depth" ]; do
        indent="$indent  "
        i=$((i + 1))
    done
    full="$indent$label"
    len=${#full}
    [ "$len" -gt "$maxlen" ] && maxlen=$len
done < "$dirs_tmp"

total=$(wc -l < "$dirs_tmp")
idx=0

{
    echo "$NAME"

    while IFS= read -r d; do
        idx=$((idx + 1))
        rel="${d#$DIR/}"
        label="$(basename "$d")/"

        # depth = number of slashes in rel
        depth=$(printf '%s' "$rel" | awk -F/ '{print NF-1}')
        indent=""
        i=0
        while [ "$i" -lt "$depth" ]; do
            indent="$indent  "
            i=$((i + 1))
        done

        # collect files directly inside this dir (no extension)
        names=""
        while IFS= read -r file; do
            [ -z "$file" ] && continue
            base="$(basename "$file")"
            base="${base%.*}"
            if [ -z "$names" ]; then
                names="$base"
            else
                names="$names, $base"
            fi
        done <<EOF
$(find "$d" -mindepth 1 -maxdepth 1 -type f | LC_ALL=C sort)
EOF

        full="$indent$label"

        if [ "$idx" -eq "$total" ]; then
            prefix="└──"
        else
            prefix="├──"
        fi

        pad=$((maxlen - ${#full} + 4))
        printf '%s %s%*s%s\n' "$prefix" "$full" "$pad" "" "$names"
    done < "$dirs_tmp"
} > "$OUT"

rm -f "$dirs_tmp" "$folders_tmp" 2>/dev/null

echo "Wrote: $OUT"