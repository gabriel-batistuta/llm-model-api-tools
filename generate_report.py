import json
import os
import glob

def analyze_results():
    results = []
    
    # Padrão para arquivos agregados
    aggregated_files = glob.glob("results/aggregated-*.json")
    
    for agg_file in sorted(aggregated_files):
        # Extrair configuração do nome do arquivo
        filename = os.path.basename(agg_file)
        parts = filename.replace("aggregated-", "").replace(".json", "").split("-")
        config = parts[0]
        prompt = parts[1]
        scenario = parts[2]
        
        with open(agg_file, 'r') as f:
            data = json.load(f)
        
        # Coletar métricas
        correctness_ratio = data['correctnessRatio']
        correct_runs = data['correctRuns']
        total_runs = data['totalRuns']
        consistent = data['consistent']
        tool_usage = data['toolUsage']
        
        # Determinar abordagem
        if config in ['CONF1', 'CONF2']:
            approach = "BankToolsA" if config == 'CONF1' else "BankToolsB"
        else:
            # CONF3 e CONF4 - ver qual ferramenta foi mais usada
            tools_used = list(tool_usage.keys())
            if len(tools_used) == 1:
                approach = tools_used[0]
            else:
                approach = f"{tools_used[0]}/{tools_used[1]}"
        
        results.append({
            'config': config,
            'prompt': prompt,
            'scenario': scenario,
            'corretude': f"{correct_runs}/{total_runs} ({correctness_ratio*100:.1f}%)",
            'consistencia': "Sim" if consistent else "Não",
            'abordagem': approach,
            'tool_usage': tool_usage,
            'correctness_ratio': correctness_ratio,
            'consistent': consistent
        })
    
    return results

def generate_detailed_analysis(results):
    print("=" * 80)
    print("RELATÓRIO DE RESULTADOS - PROJETO LLM TOOLS")
    print("=" * 80)
    
    # Tabela principal
    print("\nTABELA DE RESULTADOS")
    print("-" * 80)
    print(f"{'Config':<8} {'Prompt':<6} {'Cenário':<8} {'Corretude':<12} {'Consistência':<12} {'Abordagem':<15}")
    print("-" * 80)
    
    for result in results:
        print(f"{result['config']:<8} {result['prompt']:<6} {result['scenario']:<8} "
              f"{result['corretude']:<12} {result['consistencia']:<12} {result['abordagem']:<15}")
    
    # Análise de casos problemáticos
    print("\n" + "=" * 80)
    print("ANÁLISE DETALHADA DE CASOS PROBLEMÁTICOS")
    print("=" * 80)
    
    problematic_cases = [r for r in results if r['correctness_ratio'] < 1.0 or not r['consistent']]
    
    if not problematic_cases:
        print("✓ Nenhum caso problemático encontrado - todas as execuções foram corretas e consistentes!")
    else:
        for case in problematic_cases:
            print(f"\n🔍 CASO PROBLEMÁTICO: {case['config']}-{case['prompt']}-{case['scenario']}")
            print(f"   - Corretude: {case['corretude']}")
            print(f"   - Consistência: {case['consistencia']}")
            print(f"   - Ferramentas utilizadas: {case['tool_usage']}")
            
            # Buscar detalhes das execuções individuais
            summary_pattern = f"results/summary-{case['config']}-{case['prompt']}-{case['scenario']}-run*.json"
            summary_files = glob.glob(summary_pattern)
            
            incorrect_runs = []
            for summary_file in sorted(summary_files):
                with open(summary_file, 'r') as f:
                    summary_data = json.load(f)
                
                if not summary_data['evaluation']['correct']:
                    run_id = summary_data['runId']
                    mismatches = summary_data['evaluation']['mismatches']
                    observed_ops = summary_data['evaluation']['observedOps']
                    incorrect_runs.append({
                        'run': run_id,
                        'mismatches': mismatches,
                        'observed_ops': observed_ops
                    })
            
            if incorrect_runs:
                print(f"   - Execuções incorretas: {len(incorrect_runs)}")
                for run in incorrect_runs[:3]:  # Mostrar até 3 execuções problemáticas
                    print(f"     * Execução {run['run'][:8]}...:")
                    for mismatch in run['mismatches']:
                        print(f"       - {mismatch}")
                    print(f"       Operações observadas: {run['observed_ops']}")

def main():
    if not os.path.exists("results"):
        print("❌ Diretório 'results' não encontrado!")
        return
    
    results = analyze_results()
    generate_detailed_analysis(results)

if __name__ == "__main__":
    main()