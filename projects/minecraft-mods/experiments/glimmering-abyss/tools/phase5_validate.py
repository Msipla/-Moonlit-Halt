"""Check original client frame metadata and runtime structure; never score aesthetics."""
import csv,json,re

def validate(output):
 rows=json.loads((output/'screenshots/captures.json').read_text());assert len(rows)==14
 raw=json.loads((output/'frame-intervals.json').read_text());assert set(raw)=={r['file'] for r in rows}
 for r in rows:
  assert r['gl_error_at_capture']==0 and re.search(r'pC: 0+, pU: 0+',r['chunk_debug']),r['file']
  assert r['environment']['native_lightmap_rgb']==r['environment']['display_lightmap_rgb']
  assert r['space_lighting']['active']==(r['render_profile']!='a' and r['kind']!='dimension_scope')
  values=sorted(raw[r['file']]);assert len(values)==r['sample_frames'] and all(x>0 for x in values)
  assert abs(len(values)*1000/sum(values)-r['sample_fps'])<1e-6
 before=next(r for r in rows if r['file']=='before-platform.png');after=next(r for r in rows if r['file']=='blue-platform.png')
 for k in ['x','y','z','yaw','pitch','fov','brightness_gamma','view_distance_chunks','framebuffer_width','framebuffer_height','space_lighting']:
  if k=='space_lighting':assert before[k]['parameters']==after[k]['parameters']
  else:assert before[k]==after[k],k
 p=output
 while p.name!='phase5' and p.parent!=p:p=p.parent
 baseline=json.loads((p.parent/'phase1/summary.json').read_text())
 for name in ['baseline','reference','glimmer','glimmer-before-building']:
  a=json.loads((output/f'audit-{name}.json').read_text());assert a['native_light_profile_pass'] and a['route_and_rail_pass'];assert a['generated_terrain_sha256']==baseline['terrain_fingerprint']
 audits=[json.loads((output/f'exploration-{name}.json').read_text()) for name in ['first','repeat','reload']]
 assert all(a['pass'] and a['template_entities']==0 for a in audits),audits
 assert len({a['actual_structure_state_sha256'] for a in audits})==1
 launch=json.loads((output/'client-launch-provenance.json').read_text());assert launch['development_classes'] is False
 walk=json.loads((output/'walk-collision.json').read_text());assert walk['walk_and_collision_pass']
 rail=json.loads((output/'rail-collision.json').read_text());assert rail['rail_collision_pass']
 legs=json.loads((output/'walk-legs.json').read_text());assert len(legs)==10 and all(x['pass'] for x in legs)
 regression=json.loads((output/'architecture-phase4-regression.json').read_text());assert regression['pass']
 old=json.loads((p.parent/'phase4/runs/38250075059/architecture-first.json').read_text())
 assert regression['actual_structure_block_id_sha256']==old['actual_structure_block_id_sha256']
 facade_before=next(r for r in rows if r['file']=='before-facade.png');facade_after=next(r for r in rows if r['file']=='blue-facade.png')
 for k in ['x','y','z','yaw','pitch','fov','brightness_gamma']:assert facade_before[k]==facade_after[k],k
 keys=['file','render_profile','view','kind','sample_fps','p95_frame_ms','max_frame_ms','sample_frames','chunk_debug']
 with (output/'performance.csv').open('w',newline='') as f:
  w=csv.DictWriter(f,fieldnames=keys);w.writeheader();w.writerows({k:r[k] for k in keys} for r in rows)
 return dict(status='TECHNICAL_PASS',official_release_client_loaded=True,all_screenshot_queues_drained=True,original_lightmap_unchanged=True,legacy_terrain_fingerprint_before_placement_matches=True,structure_placement_repeat_reload_pass=True,connected_walkable_route_pass=True,actual_client_full_route_return_walk_pass=True,actual_client_iron_rail_collision_pass=True,phase4_original_template_regression_pass=True,finite_shaft_floor_y=-63,template_block_count=audits[0]['template_blocks_checked'],warm_windows_native_light_12=audits[0]['warm_windows_native_light_12'],same_view_before_after_pass=True)
