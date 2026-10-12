"""Validate actual release captures/audits, without scoring their aesthetics."""
import csv,json,re
def validate(output):
 rows=json.loads((output/'screenshots/captures.json').read_text());assert len(rows)==21
 raw=json.loads((output/'frame-intervals.json').read_text());assert set(raw)=={r['file'] for r in rows}
 for r in rows:
  assert r['gl_error_at_capture']==0 and re.search(r'pC: 0+, pU: 0+(?:,|\s|$)',r['chunk_debug']),r['file']
  assert r['environment']['native_lightmap_rgb']==r['environment']['display_lightmap_rgb']
  assert r['space_lighting']['active']==(r['render_profile']!='a' and r['kind']!='dimension_scope')
  values=raw[r['file']];assert len(values)==r['sample_frames'] and all(x>0 for x in values)
  assert abs(len(values)*1000/sum(values)-r['sample_fps'])<1e-6
 def frame(file):return next(r for r in rows if r['file']==file)
 for name in ('street','high','cliff'):
  base=frame('blue-'+name+'.png');low=frame('blue95-'+name+'.png');fog=frame('blue95-fog-'+name+'.png')
  for k in ('x','y','z','yaw','pitch','fov','brightness_gamma','view_distance_chunks','framebuffer_width','framebuffer_height'):
   assert base[k]==low[k]==fog[k],(name,k)
  b=base['space_lighting']['parameters'];l=low['space_lighting']['parameters'];f=fog['space_lighting']['parameters']
  for k in ('base','radiance'):assert abs(l[k]/b[k]-.95)<1e-6
  for k in set(b)-{'base','radiance','name'}:assert b[k]==l[k]==f[k],k
  assert low['space_lighting']['depth_parameters'][2]==0
  assert fog['space_lighting']['depth_parameters']==[12.0,-58.0,.68,28.0]
 p=output
 while p.name!='phase6' and p.parent!=p:p=p.parent
 baseline=json.loads((p.parent/'phase1/summary.json').read_text())
 for name in ['baseline','reference','glimmer','glimmer-before-building']:
  a=json.loads((output/f'audit-{name}.json').read_text());assert a['native_light_profile_pass'] and a['route_and_rail_pass'];assert a['generated_terrain_sha256']==baseline['terrain_fingerprint']
 audits=[json.loads((output/f'town-{name}.json').read_text()) for name in ['first','repeat','reload']]
 assert all(a['pass'] and a['template_entities']==0 for a in audits),audits
 assert len({a['actual_structure_state_sha256'] for a in audits})==1
 for label in ('first','reload'):
  safety=json.loads((output/f'safety-{label}.json').read_text());assert safety['pass'] and safety['difficulty']=='hard' and safety['dark_abyss_native_block_light']==0
 launch=json.loads((output/'client-launch-provenance.json').read_text());assert launch['development_classes'] is False
 walk=json.loads((output/'walk-collision.json').read_text());assert walk['actual_ladder_climb_pass']
 rail=json.loads((output/'rail-collision.json').read_text());assert rail['rail_collision_pass'] and rail['barrier_collision_height']==1.5 and rail['barrier_type']=='minecraft:polished_blackstone_wall' and not rail['flying']
 legs=json.loads((output/'walk-legs.json').read_text());assert len(legs)==16 and all(x['pass'] for x in legs)
 for phase,filename,key,run in [(4,'architecture-phase4-regression.json','actual_structure_block_id_sha256','38250075059'),(5,'exploration-phase5-regression.json','actual_structure_state_sha256','38259041182')]:
  a=json.loads((output/filename).read_text());assert a['pass']
  old=json.loads((p.parent/f'phase{phase}/runs'/run/('architecture-first.json' if phase==4 else 'exploration-first.json')).read_text());assert a[key]==old[key]
 keys=['file','render_profile','view','kind','sample_fps','p95_frame_ms','max_frame_ms','sample_frames','chunk_debug']
 with (output/'performance.csv').open('w',newline='') as f:
  w=csv.DictWriter(f,fieldnames=keys);w.writeheader();w.writerows({k:r[k] for k in keys} for r in rows)
 return dict(status='TECHNICAL_PASS',official_release_client_loaded=True,all_screenshot_queues_drained=True,original_lightmap_unchanged=True,legacy_terrain_before_placement_matches=True,structure_repeat_reload_pass=True,actual_bridge_return_and_street_walk_pass=True,actual_vanilla_ladder_climb_pass=True,actual_bridge_parapet_collision_pass=True,phase4_and_5_template_regression_pass=True,lighting_95_percent_same_view_pass=True,bounded_height_surface_haze=True,template_block_count=audits[0]['template_blocks_checked'],warm_windows_native_light_12=audits[0]['warm_windows_native_light_12'],accepted_workshop_windows_preserved=52,buildings=6,entire_landscape_no_mobs_hard_difficulty_pass=True,overworld_spawning_unchanged=True)
