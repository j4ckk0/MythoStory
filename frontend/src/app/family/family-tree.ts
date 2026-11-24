import { Component, ElementRef, afterRenderEffect, computed, inject, input, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { CHARACTER_KINDS, CatalogService, Character } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';
import { I18nService } from '../i18n/i18n.service';
import { CharacterChip } from '../shared/character-chip';
import { normalize } from '../shared/text';

/** How many generations of ancestors the tree shows. */
const ANCESTOR_GENERATIONS = 2;
const NODE_WIDTH = 124;
const NODE_HEIGHT = 34;
const ROW_HEIGHT = 64;

/** A box of the pedigree: a character, its place, and the line to the child it is a parent of. */
interface Node {
  character: Character;
  x: number;
  y: number;
  childX?: number;
  childY?: number;
  root: boolean;
}

interface AncestorTree {
  character: Character;
  parents: AncestorTree[];
}

/**
 * A character's family: their ancestors as a pedigree — the character at the bottom, parents and grandparents above
 * (further up, the gods' families repeat themselves: Ouranos and Gaia four times over for Hebe) —, and their children grouped by the other parent ("with Leto: Apollo, Artemis"), which keeps Zeus's hundred
 * children readable. Every name leads to that character's own tree.
 */
@Component({
  selector: 'app-family-tree',
  imports: [I18nPipe, I18nTextPipe, CharacterChip],
  templateUrl: './family-tree.html',
  styleUrl: './family-tree.css',
})
export class FamilyTree {
  private readonly catalog = inject(CatalogService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  readonly character = input.required<Character>();
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('pedigree');

  constructor() {
    // a pedigree wider than the window scrolls: it opens on the character, at its bottom's middle
    afterRenderEffect(() => {
      const tree = this.pedigree();
      const element = this.scroller()?.nativeElement;
      const root = tree?.nodes.find((n) => n.root);
      if (element && root) element.scrollLeft = root.x + NODE_WIDTH / 2 - element.clientWidth / 2;
    });
  }

  protected readonly nodeWidth = NODE_WIDTH;
  protected readonly nodeHeight = NODE_HEIGHT;

  /** The pedigree, laid out: each leaf gets a column, each parent sits above the middle of its own parents. */
  protected readonly pedigree = computed(() => {
    const tree = this.ancestors(this.character(), 0, new Set());
    if (!tree.parents.length) return null;
    const nodes: Node[] = [];
    let column = 0;
    const depth = (t: AncestorTree): number => 1 + Math.max(0, ...t.parents.map(depth));
    const rows = depth(tree);
    // a character may appear twice (Cronus, Hebe's grandfather through Zeus and through Hera): each box is linked
    // to its child as it is laid out, never looked up
    const place = (t: AncestorTree, level: number): Node => {
      const parents = t.parents.map((p) => place(p, level + 1));
      const xs = parents.map((p) => p.x);
      const x = xs.length ? (Math.min(...xs) + Math.max(...xs)) / 2 : column++ * NODE_WIDTH * 1.1;
      const node: Node = { character: t.character, x, y: (rows - 1 - level) * ROW_HEIGHT, root: level === 0 };
      for (const parent of parents) {
        parent.childX = node.x;
        parent.childY = node.y;
      }
      nodes.push(node);
      return node;
    };
    place(tree, 0);
    const width = Math.max(...nodes.map((n) => n.x)) + NODE_WIDTH;
    return { nodes, width, height: rows * ROW_HEIGHT - (ROW_HEIGHT - NODE_HEIGHT) };
  });

  /** The children by other parent, those with a known other parent first, each group and its children by name. */
  protected readonly families = computed(() => {
    const me = this.character().id;
    const groups = new Map<string, { others: Character[]; children: Character[] }>();
    for (const child of this.catalog.childrenByCharacter().get(me) ?? []) {
      const others = this.catalog.charactersOf((child.parents ?? []).filter((id) => id !== me));
      const key = others.map((o) => o.id).sort().join('+');
      const group = groups.get(key) ?? { others, children: [] };
      group.children.push(child);
      groups.set(key, group);
    }
    const name = (c: Character) => normalize(this.i18n.text(c.name));
    const byKindAndName = (a: Character, b: Character) =>
      CHARACTER_KINDS.indexOf(a.kind) - CHARACTER_KINDS.indexOf(b.kind) || name(a).localeCompare(name(b));
    return [...groups.values()]
      .map((g) => ({ ...g, children: g.children.sort(byKindAndName) }))
      .sort((a, b) => Number(!a.others.length) - Number(!b.others.length) || b.children.length - a.children.length);
  });

  protected childCount(character: Character): number {
    return this.catalog.childrenByCharacter().get(character.id)?.length ?? 0;
  }

  protected open(character: Character): void {
    this.router.navigate(['/characters', character.id]);
  }

  private ancestors(character: Character, level: number, seen: Set<string>): AncestorTree {
    const parents =
      level < ANCESTOR_GENERATIONS && !seen.has(character.id)
        ? this.catalog.charactersOf(character.parents)
        : [];
    const path = new Set(seen).add(character.id);
    return { character, parents: parents.map((p) => this.ancestors(p, level + 1, path)) };
  }
}
