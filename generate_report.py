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
        
        # Determinar abordagem (CORREÇÃO: considerar uso real, não apenas configuração)
        tools_used = list(tool_usage.keys())
        if len(tools_used) == 1:
            approach = tools_used[0]
        else:
            # Ordenar por frequência de uso
            sorted_tools = sorted(tools_used, key=lambda x: tool_usage[x], reverse=True)
            approach = "/".join(sorted_tools)
        
        # Detectar se houve falhas de execução (arquivos summary com erro)
        error_runs = 0
        summary_pattern = f"results/summary-{config}-{prompt}-{scenario}-run*.json"
        summary_files = glob.glob(summary_pattern)
        
        for summary_file in summary_files:
            with open(summary_file, 'r') as f:
                summary_data = json.load(f)
            if "[ERROR]" in summary_data.get('llmResponseText', ''):
                error_runs += 1
        
        results.append({
            'config': config,
            'prompt': prompt,
            'scenario': scenario,
            'corretude': f"{correct_runs}/{total_runs} ({correctness_ratio*100:.1f}%)",
            'consistencia': "Sim" if consistent else "Não",
            'abordagem': approach,
            'tool_usage': tool_usage,
            'correctness_ratio': correctness_ratio,
            'consistent': consistent,
            'error_runs': error_runs,
            'total_runs': total_runs
        })
    
    return results

def generate_detailed_analysis(results):
    print("=" * 100)
    print("RELATÓRIO DE RESULTADOS - PROJETO LLM TOOLS")
    print("AVALIAÇÃO DO MODELO MISTRAL:LATEST")
    print("=" * 100)
    
    # Tabela principal
    print("\nTABELA DE RESULTADOS - MÉTRICAS AGRAGADAS")
    print("-" * 100)
    print(f"{'Config':<8} {'Prompt':<6} {'Cenário':<8} {'Corretude':<15} {'Consistência':<12} {'Abordagem':<20} {'Erros':<8}")
    print("-" * 100)
    
    for result in results:
        error_info = f"{result['error_runs']}/{result['total_runs']}" if result['error_runs'] > 0 else "0"
        print(f"{result['config']:<8} {result['prompt']:<6} {result['scenario']:<8} "
              f"{result['corretude']:<15} {result['consistencia']:<12} {result['abordagem']:<20} {error_info:<8}")
    
    # Análise de casos problemáticos
    print("\n" + "=" * 100)
    print("ANÁLISE DETALHADA DE CASOS PROBLEMÁTICOS")
    print("=" * 100)
    
    problematic_cases = [r for r in results if r['correctness_ratio'] < 1.0 or not r['consistent'] or r['error_runs'] > 0]
    
    if not problematic_cases:
        print("✓ Nenhum caso problemático encontrado - todas as execuções foram corretas e consistentes!")
    else:
        for case in problematic_cases:
            print(f"\n🔍 CASO PROBLEMÁTICO: {case['config']}-{case['prompt']}-{case['scenario']}")
            print(f"   - Corretude: {case['corretude']}")
            print(f"   - Consistência: {case['consistencia']}")
            print(f"   - Abordagem (Ferramentas): {case['abordagem']}")
            print(f"   - Execuções com erro: {case['error_runs']}/{case['total_runs']}")
            print(f"   - Uso de ferramentas: {case['tool_usage']}")
            
            # Buscar detalhes das execuções individuais
            summary_pattern = f"results/summary-{case['config']}-{case['prompt']}-{case['scenario']}-run*.json"
            summary_files = glob.glob(summary_pattern)
            
            incorrect_runs = []
            error_runs = []
            
            for summary_file in sorted(summary_files):
                with open(summary_file, 'r') as f:
                    summary_data = json.load(f)
                
                # Verificar se é um erro de execução
                if "[ERROR]" in summary_data.get('llmResponseText', ''):
                    error_runs.append({
                        'run': summary_data['runId'],
                        'error': summary_data['llmResponseText']
                    })
                # Verificar se é uma execução incorreta
                elif not summary_data['evaluation']['correct']:
                    mismatches = summary_data['evaluation']['mismatches']
                    observed_ops = summary_data['evaluation']['observedOps']
                    incorrect_runs.append({
                        'run': summary_data['runId'],
                        'mismatches': mismatches,
                        'observed_ops': observed_ops
                    })
            
            if error_runs:
                print(f"   - Execuções com FALHA (erro no LLM): {len(error_runs)}")
                for run in error_runs[:2]:  # Mostrar até 2 erros
                    print(f"     * Execução {run['run'][:8]}...: {run['error'][:100]}...")
            
            if incorrect_runs:
                print(f"   - Execuções INCORRETAS (lógica errada): {len(incorrect_runs)}")
                for run in incorrect_runs[:2]:  # Mostrar até 2 execuções problemáticas
                    print(f"     * Execução {run['run'][:8]}...:")
                    for mismatch in run['mismatches'][:2]:  # Mostrar até 2 mismatches
                        print(f"       - {mismatch}")
                    if len(run['mismatches']) > 2:
                        print(f"       - ... e mais {len(run['mismatches']) - 2} problemas")
                    print(f"       Operações observadas: {run['observed_ops']}")

def generate_mistral_analysis():
    print("\n" + "=" * 100)
    print("ANÁLISE ESPECÍFICA - FALHA DO MODELO MISTRAL NO CONF3 P3 A")
    print("=" * 100)
    
    print("\n📉 **PROBLEMA IDENTIFICADO:**")
    print("O modelo Mistral:latest apresentou falhas críticas a partir da configuração CONF3, Prompt P3, Cenário A.")
    print("Este ponto marca uma transição onde o modelo começou a gerar parâmetros inválidos para as ferramentas.")
    
    print("\n🔍 **DETALHES DO ERRO:**")
    print("Erro capturado durante a execução:")
    print("""
ERROR in LLM call: LLM call failed: LangChain4J call failed: null
Exception in thread "main" java.lang.RuntimeException: LLM call failed for config CONF3 prompt P3 scenario A
Caused by: java.lang.RuntimeException: LLM call failed: LangChain4J call failed: null
Caused by: java.util.concurrent.ExecutionException: java.lang.RuntimeException: LangChain4J call failed: null  
Caused by: java.lang.RuntimeException: LangChain4J call failed: null
Caused by: java.lang.IllegalArgumentException
    """)
    
    print("\n💡 **ANÁLISE TÉCNICA:**")
    print("1. **Parâmetros Inválidos**: O Mistral gerou JSON malformado para as ferramentas")
    print("2. **Tentativa de Lógica Condicional**: O modelo tentou usar condicionais dentro dos parâmetros")
    print("   Exemplo do erro: '{\"if\": \"$[0].success && $[1].success\"}'")
    print("3. **Incompatibilidade com LangChain4J**: O framework não suporta lógica complexa em parâmetros")
    
    print("\n📊 **CONTEXTO DA FALHA:**")
    print("• **Antes do CONF3 P3 A**: Execuções bem-sucedidas em CONF1, CONF2, CONF3 P1, CONF3 P2")
    print("• **A partir do CONF3 P3 A**: Falhas consistentes devido à complexidade do prompt")
    print("• **Prompt P3**: Envolve múltiplas operações condicionais aninhadas")
    print("• **CONF3**: Primeira configuração com ambas as ferramentas disponíveis")
    
    print("\n🎯 **IMPLICAÇÕES PARA O PROJETO:**")
    print("✓ **Limitação do Modelo**: Mistral tem dificuldade com prompts complexos e múltiplas ferramentas")
    print("✓ **Validação do Objetivo**: Comprova a necessidade de avaliar diferentes LLMs com as mesmas APIs")  
    print("✓ **Dado Relevante**: A falha é um resultado válido para análise comparativa entre modelos")

def main():
    if not os.path.exists("results"):
        print("❌ Diretório 'results' não encontrado!")
        return
    
    print("🔍 Analisando resultados das execuções...")
    results = analyze_results()
    
    generate_detailed_analysis(results)
    generate_mistral_analysis()
    
    with open('results/relatorio_completo.txt', 'w') as f:
        import sys
        original_stdout = sys.stdout
        sys.stdout = f
        generate_detailed_analysis(results)
        generate_mistral_analysis()
        sys.stdout = original_stdout
    
    print(f"\n💾 Relatório completo salvo em: results/relatorio_completo.txt")

if __name__ == "__main__":
    main()