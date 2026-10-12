"""Validate actual spatial-shader controls; aesthetic acceptance belongs to the user."""
import json,csv,statistics,re
def validate(output):
    root=output.parent
    rows=json.loads((output/'screenshots/captures.json').read_text())
    assert len(rows)==19
    same=['x','y','z','yaw','pitch','fov','brightness_gamma','framebuffer_width','framebuffer_height','view_distance_chunks','simulation_distance_chunks','graphics','ambient_occlusion','dimension_ambient_light','dimension_fixed_time','light_mode']
    for view in ['tunnel','platform','edge','overlook']:
        pair=[r for r in rows if r['kind']=='comparison' and r['view']==view]
        assert len(pair)==2 and {r['render_profile'] for r in pair}=={'blue','copper'}
        assert all([r[k] for k in same]==[pair[0][k] for k in same] for r in pair)
    for r in rows:
        e=r['environment'];space=r['space_lighting']
        assert r['gl_error_at_capture']==0 and re.search(r'pC: 0+, pU: 0+',r['chunk_debug']),r['file']
        assert e['native_lightmap_rgb']==e['display_lightmap_rgb'],r['file']
        if r['render_profile']!='a' and r['kind']!='dimension_scope':
            assert space['active'] and space['shader_ready'] and space['terrain_shader_selections']>0
            assert space['parameters']['airMax']<=.35
        else:assert not space['active']
    audits=[json.loads((output/f'audit-glimmer-{p}.json').read_text()) for p in ['blue','copper']]
    assert all(a['native_light_profile_pass'] and a['route_and_rail_pass'] for a in audits)
    assert audits[0]['light_profile']==audits[1]['light_profile']
    fingerprints={a['generated_terrain_sha256'] for a in audits}
    assert len(fingerprints)==1
    previous=output
    while previous.name!='phase3' and previous.parent!=previous:previous=previous.parent
    reference=json.loads((previous.parent/'phase1/summary.json').read_text())
    assert next(iter(fingerprints))==reference['terrain_fingerprint']
    for p in ['blue','copper']:
        a=json.loads((output/f'audit-baseline-no-sources-{p}.json').read_text())
        assert all(s['block']==s['sky']==0 for s in a['light_profile'])
    raw=json.loads((output/'frame-intervals.json').read_text())
    assert set(raw)=={r['file'] for r in rows}
    for r in rows:
        values=sorted(raw[r['file']]);assert len(values)==r['sample_frames'] and all(x>0 for x in values)
        assert abs(len(values)*1000/sum(values)-r['sample_fps'])<1e-6
        assert abs(values[int((len(values)-1)*.95)]-r['p95_frame_ms'])<1e-6
    keys=['file','render_profile','view','kind','sample_fps','median_frame_ms','p95_frame_ms','max_frame_ms','sample_frames','frames_over_100ms','chunk_debug']
    with (output/'performance.csv').open('w',newline='') as f:
        w=csv.DictWriter(f,fieldnames=keys);w.writeheader();w.writerows({k:r[k] for k in keys} for r in rows)
    blue=next(r for r in rows if r['file']=='blue-platform.png')['space_lighting']['parameters']
    copper=next(r for r in rows if r['file']=='copper-platform.png')['space_lighting']['parameters']
    assert blue['color']!=copper['color'] and blue['airColor']!=copper['airColor']
    assert all(blue[k]==copper[k] for k in ['base','anchor','radius','radiance','direction','airStart','airEnd','airMax'])
    return dict(status='TECHNICAL_PASS',comparison_screenshots=8,same_view_conditions_pass=True,
        original_lightmap_unchanged=True,original_native_light_unchanged=True,zero_source_control_pass=True,
        terrain_fingerprint=next(iter(fingerprints)),matches_phase1_terrain_fingerprint=True,
        shader_scope_control_pass=True,all_capture_chunk_queues_drained=True,raw_frame_intervals_verified=True,
        alternate_color_same_spatial_logic_pass=True,aesthetic_acceptance='Pending user review of raw Phase 3 frames',
        performance_limit='Hosted software OpenGL only; not Windows/PCL2 or the target GPU')
